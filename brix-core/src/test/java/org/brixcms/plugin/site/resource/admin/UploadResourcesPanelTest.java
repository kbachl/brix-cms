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

package org.brixcms.plugin.site.resource.admin;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.jcr.Binary;

import org.apache.commons.fileupload2.core.FileItem;
import org.apache.wicket.markup.html.form.upload.FileUpload;
import org.brixcms.jcr.api.JcrValueFactory;
import org.easymock.EasyMock;
import org.junit.Test;

public class UploadResourcesPanelTest {
    @Test
    public void createUploadBinaryClosesTheUploadStreamAfterSuccess() throws Exception {
        CloseTrackingInputStream input = new CloseTrackingInputStream();
        FileItem item = uploadItem(input);
        JcrValueFactory valueFactory = EasyMock.createMock(JcrValueFactory.class);
        Binary expected = EasyMock.createMock(Binary.class);
        EasyMock.expect(valueFactory.createBinary(input)).andReturn(expected);
        EasyMock.replay(valueFactory, expected);

        Binary actual = UploadResourcesPanel.createUploadBinary(new FileUpload(item), valueFactory);

        assertSame(expected, actual);
        assertTrue(input.closed);
        EasyMock.verify(valueFactory, expected);
    }

    @Test
    public void createUploadBinaryClosesTheUploadStreamWhenBinaryCreationFails() throws Exception {
        CloseTrackingInputStream input = new CloseTrackingInputStream();
        FileItem item = uploadItem(input);
        JcrValueFactory valueFactory = EasyMock.createMock(JcrValueFactory.class);
        EasyMock.expect(valueFactory.createBinary(input)).andThrow(new IllegalStateException("storage failed"));
        EasyMock.replay(valueFactory);

        try {
            UploadResourcesPanel.createUploadBinary(new FileUpload(item), valueFactory);
        } catch (IllegalStateException e) {
            assertTrue(input.closed);
            EasyMock.verify(valueFactory);
            return;
        }

        throw new AssertionError("Expected binary creation to fail");
    }

    @SuppressWarnings("rawtypes")
    private static FileItem uploadItem(InputStream input) throws IOException {
        FileItem item = EasyMock.createMock(FileItem.class);
        EasyMock.expect(item.getInputStream()).andReturn(input);
        EasyMock.replay(item);
        return item;
    }

    private static class CloseTrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private CloseTrackingInputStream() {
            super(new byte[] { 1 });
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
