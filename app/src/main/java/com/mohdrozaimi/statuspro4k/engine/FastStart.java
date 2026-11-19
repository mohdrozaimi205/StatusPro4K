package com.mohdrozaimi.statuspro4k.engine;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java MP4 "fast start" (same idea as qt-faststart): moves the 'moov' box in front of 'mdat'
 * so the video can start playing / uploading before the whole file is read, and patches every
 * chunk offset (stco / co64) by the size of the moved moov box.
 */
public final class FastStart {

    private FastStart() {}

    private static final class Box {
        final long offset;
        final long size;
        final String type;
        Box(long offset, long size, String type) { this.offset = offset; this.size = size; this.type = type; }
    }

    /**
     * @return true if {@code out} was written with moov first; false if the input was already
     *     fast-start (or could not be handled) and nothing was written.
     */
    public static boolean process(File in, File out) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(in, "r")) {
            FileChannel ch = raf.getChannel();
            long fileSize = ch.size();
            List<Box> boxes = new ArrayList<>();
            long pos = 0;
            ByteBuffer hdr = ByteBuffer.allocate(16);
            while (pos + 8 <= fileSize) {
                hdr.clear();
                hdr.limit(8);
                ch.read(hdr, pos);
                hdr.flip();
                long size = hdr.getInt() & 0xFFFFFFFFL;
                byte[] t = new byte[4];
                hdr.get(t);
                String type = new String(t, "ISO-8859-1");
                if (size == 1) {
                    hdr.clear();
                    hdr.limit(8);
                    ch.read(hdr, pos + 8);
                    hdr.flip();
                    size = hdr.getLong();
                } else if (size == 0) {
                    size = fileSize - pos;
                }
                if (size < 8 || pos + size > fileSize) throw new IOException("Bad box " + type + " at " + pos);
                boxes.add(new Box(pos, size, type));
                pos += size;
            }

            Box moov = null;
            int firstMdatIdx = -1;
            for (int i = 0; i < boxes.size(); i++) {
                Box b = boxes.get(i);
                if (b.type.equals("moov")) moov = b;
                if (b.type.equals("mdat") && firstMdatIdx < 0) firstMdatIdx = i;
            }
            if (moov == null || firstMdatIdx < 0) return false;
            if (moov.offset < boxes.get(firstMdatIdx).offset) return false; // already fast-start
            if (moov.size > Integer.MAX_VALUE) return false;

            ByteBuffer moovBuf = ByteBuffer.allocate((int) moov.size);
            ch.read(moovBuf, moov.offset);
            moovBuf.flip();
            patchContainer(moovBuf, 8, (int) moov.size, moov.size);
            moovBuf.rewind();

            try (RandomAccessFile wout = new RandomAccessFile(out, "rw")) {
                wout.setLength(0);
                FileChannel oc = wout.getChannel();
                for (int i = 0; i < firstMdatIdx; i++) {
                    Box b = boxes.get(i);
                    if (b == moov) continue;
                    copy(ch, b.offset, b.size, oc);
                }
                while (moovBuf.hasRemaining()) oc.write(moovBuf);
                for (int i = firstMdatIdx; i < boxes.size(); i++) {
                    Box b = boxes.get(i);
                    if (b == moov) continue;
                    copy(ch, b.offset, b.size, oc);
                }
            }
            return true;
        }
    }

    private static void copy(FileChannel src, long offset, long size, FileChannel dst) throws IOException {
        long done = 0;
        while (done < size) {
            long n = src.transferTo(offset + done, size - done, dst);
            if (n <= 0) throw new IOException("copy failed");
            done += n;
        }
    }

    private static boolean isContainer(String type) {
        return type.equals("moov") || type.equals("trak") || type.equals("mdia")
                || type.equals("minf") || type.equals("stbl") || type.equals("edts");
    }

    /** Walks child boxes in [start, end) of buf and shifts chunk offsets. */
    private static void patchContainer(ByteBuffer buf, int start, int end, long shift) throws IOException {
        int p = start;
        while (p + 8 <= end) {
            long size = buf.getInt(p) & 0xFFFFFFFFL;
            byte[] t = new byte[4];
            for (int i = 0; i < 4; i++) t[i] = buf.get(p + 4 + i);
            String type = new String(t, "ISO-8859-1");
            int headerLen = 8;
            if (size == 1) {
                size = buf.getLong(p + 8);
                headerLen = 16;
            } else if (size == 0) {
                size = end - p;
            }
            if (size < headerLen || p + size > end) throw new IOException("Bad child box " + type);
            int body = p + headerLen;
            if (isContainer(type)) {
                patchContainer(buf, body, (int) (p + size), shift);
            } else if (type.equals("stco")) {
                int count = buf.getInt(body + 4);
                for (int i = 0; i < count; i++) {
                    int at = body + 8 + i * 4;
                    long v = (buf.getInt(at) & 0xFFFFFFFFL) + shift;
                    if (v > 0xFFFFFFFFL) throw new IOException("stco overflow; needs co64");
                    buf.putInt(at, (int) v);
                }
            } else if (type.equals("co64")) {
                int count = buf.getInt(body + 4);
                for (int i = 0; i < count; i++) {
                    int at = body + 8 + i * 8;
                    buf.putLong(at, buf.getLong(at) + shift);
                }
            }
            p += (int) size;
        }
    }

    /** Desktop test entry point. */
    public static void main(String[] args) throws Exception {
        System.out.println(process(new File(args[0]), new File(args[1])));
    }
}
