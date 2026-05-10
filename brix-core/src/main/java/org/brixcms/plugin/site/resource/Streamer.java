/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.brixcms.plugin.site.resource;

import org.apache.wicket.util.lang.Bytes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Responds stream with support for Content-Range header.
 *
 * @author Matej Knopp
 */
class Streamer {
    private final long length;
    private final InputStream inputStream;
    private final String fileName;
    private final boolean attachment;
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final boolean writeBody;

    public Streamer(long length, InputStream inputStream, String fileName, boolean attachment,
                    HttpServletRequest request, HttpServletResponse response) {
        this(length, inputStream, fileName, attachment, request, response, true);
    }

    public Streamer(long length, InputStream inputStream, String fileName, boolean attachment,
                    HttpServletRequest request, HttpServletResponse response, boolean writeBody) {
        this.length = length;
        this.inputStream = inputStream;
        this.fileName = fileName;
        this.response = response;
        this.request = request;
        this.attachment = attachment;
        this.writeBody = writeBody;
    }

    private static final int BUFFER_SIZE = (int) Bytes.kilobytes(64).bytes();

    public long stream() {
        Range range = parseRange(request.getHeader("Range"), length);
        long first = 0;
        long last = length - 1;
        long contentLength = length;

        if (range.unsatisfiable) {
            response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            response.setHeader("Content-Range", "bytes */" + length);
            response.setContentLengthLong(0);
            closeInputStream();
            return 0;
        } else if (range.partial) {
            first = range.start;
            last = range.end;
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Content-Range", "bytes " + first + "-" + last + "/" + length);
            contentLength = last - first + 1;
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
        }

        //let container do it via setContentLengthLong
        //response.setHeader("Content-Length", "" + contentLength);

        response.setContentLengthLong(contentLength);


        if (!attachment) {
            response.setHeader("Content-Disposition", "inline; filename=\"" + fileName + "\";");
        } else {
            response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\";");
        }
        response.setHeader("Accept-Ranges", "bytes");


        if (!writeBody) {
            closeInputStream();
            return 0;
        }

        long written = 0;

        try (InputStream s = new BufferedInputStream(inputStream)) {
            skipFully(s, first);

            final int bufferSize = (int) Math.min(BUFFER_SIZE, Math.max(1L, contentLength));
            final byte[] buf = new byte[bufferSize];
            final OutputStream out = response.getOutputStream();
            long left = contentLength;
            while (left > 0) {
                int howMuch = bufferSize;
                if (howMuch > left) {
                    howMuch = (int) left;
                }

                int numRead = s.read(buf, 0, howMuch);

                if (numRead == -1) {
                    throw new EOFException("Resource stream ended with " + left + " bytes left to write");
                } else if (numRead == 0) {
                    int singleByte = s.read();
                    if (singleByte == -1) {
                        throw new EOFException("Resource stream ended with " + left + " bytes left to write");
                    }
                    out.write(singleByte);
                    left--;
                    written++;
                } else {
                    out.write(buf, 0, numRead);
                    left -= numRead;
                    written += numRead;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return written;
    }

    private void closeInputStream() {
        try {
            inputStream.close();
        } catch (IOException ignore) {
        }
    }

    private void skipFully(InputStream s, long bytes) throws IOException {
        long left = bytes;
        while (left > 0) {
            long skipped = s.skip(left);
            if (skipped > 0) {
                left -= skipped;
            } else if (s.read() == -1) {
                throw new EOFException("Resource stream ended while skipping to byte " + bytes);
            } else {
                left--;
            }
        }
    }

    private Range parseRange(String range, long length) {
        if (isEmpty(range)) {
            return Range.full();
        }

        if (length <= 0 || !range.startsWith("bytes=") || range.indexOf(',') != -1) {
            return length <= 0 ? Range.unsatisfiable() : Range.full();
        }

        String[] p = range.substring("bytes=".length()).split("-", -1);
        if (p.length != 2 || (isEmpty(p[0]) && isEmpty(p[1]))) {
            return Range.full();
        }

        try {
            if (isEmpty(p[0])) {
                long suffixLength = Long.valueOf(p[1]);
                if (suffixLength <= 0) {
                    return Range.unsatisfiable();
                }
                long start = Math.max(length - suffixLength, 0);
                return Range.partial(start, length - 1);
            }

            long start = Long.valueOf(p[0]);
            if (start < 0 || start >= length) {
                return Range.unsatisfiable();
            }

            long end = isEmpty(p[1]) ? length - 1 : Long.valueOf(p[1]);
            if (end < start) {
                return Range.unsatisfiable();
            }
            return Range.partial(start, Math.min(end, length - 1));
        } catch (NumberFormatException e) {
            return Range.full();
        }
    }

    private boolean isEmpty(String s) {
        return s == null || s.length() == 0;
    }

    private static class Range {
        final long start;
        final long end;
        final boolean partial;
        final boolean unsatisfiable;

        private Range(long start, long end, boolean partial, boolean unsatisfiable) {
            this.start = start;
            this.end = end;
            this.partial = partial;
            this.unsatisfiable = unsatisfiable;
        }

        private static Range full() {
            return new Range(0, 0, false, false);
        }

        private static Range partial(long start, long end) {
            return new Range(start, end, true, false);
        }

        private static Range unsatisfiable() {
            return new Range(0, 0, false, true);
        }
    }
}
