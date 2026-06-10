package org.brixcms.jcr.wrapper;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class BrixFileNodeTest {
    @Test
    public void sha256HexHashesStreamContent() throws Exception {
        String hash = BrixFileNode.sha256Hex(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)));

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash);
    }
}
