package net.bigdb.data;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class DigestValueTest {

    private final byte[] TEST_BYTES = { 45, 64, 126, -5, -100 };
    private final byte[] TEST_BYTES_DIGEST_BYTES = {-33, 97, 2, -76, 12, -18,
            -110, 85, 61, 9, -93, -50, 98, 23, 81, 74, 125, 82, -97, 39};
    private final String TEST_BYTES_DIGEST_STRING =
            "DF6102B40CEE92553D09A3CE6217514A7D529F27";
    private final byte[] TEST_BYTES_2 = { 1, 2, 3 };

    private final String TEST_STRING = "Dummy:foobar";
    private final byte[] TEST_STRING_DIGEST_BYTES = {-43, -106, -114, -58, -62,
            -93, -86, 37, 80, 93, -9, 93, 103, -70, -102, 51, -55, 127, 77, 54};
    private final String TEST_STRING_DIGEST_STRING =
            "D5968EC6C2A3AA25505DF75D67BA9A33C97F4D36";
    private final String TEST_STRING_2 = "Another string";

    private final byte[] EMPTY_BYTES = {};

    @Test
    public void fromBytes() throws Exception {
        DigestValue digestValue = DigestValue.fromBytes(TEST_BYTES);
        byte[] digestBytes = digestValue.getDigestBytes();
        assertTrue(Arrays.equals(TEST_BYTES_DIGEST_BYTES, digestBytes));
        String digestString = digestValue.toString();
        assertThat(TEST_BYTES_DIGEST_STRING, equalTo(digestString));
    }

    @Test
    public void fromString() throws Exception {
        DigestValue digestValue = DigestValue.fromString(TEST_STRING);
        byte[] digestBytes = digestValue.getDigestBytes();
        assertTrue(Arrays.equals(TEST_STRING_DIGEST_BYTES, digestBytes));
        String digestString = digestValue.toString();
        assertThat(TEST_STRING_DIGEST_STRING, equalTo(digestString));
    }

    @Test
    public void fromBuilder() throws Exception {
        DigestValue.Builder builder = new DigestValue.Builder();
        builder.update(TEST_BYTES).update(EMPTY_BYTES);
        DigestValue digestValue = builder.getDigestValue();
        byte[] digestBytes = digestValue.getDigestBytes();
        assertTrue(Arrays.equals(TEST_BYTES_DIGEST_BYTES, digestBytes));
        String digestString = digestValue.toString();
        assertThat(TEST_BYTES_DIGEST_STRING, equalTo(digestString));
    }

    @Test
    public void comparison() throws Exception {
        DigestValue dv1 = DigestValue.fromBytes(TEST_BYTES);
        DigestValue dv2 = DigestValue.fromBytes(TEST_BYTES);
        DigestValue dv3 = DigestValue.fromBytes(TEST_BYTES_2);
        DigestValue dv4 = DigestValue.fromString(TEST_STRING);
        DigestValue dv5 = DigestValue.fromString(TEST_STRING);
        DigestValue dv6 = DigestValue.fromString(TEST_STRING_2);

        assertThat(dv1, equalTo(dv2));
        assertThat(dv1, not(equalTo(dv3)));
        assertThat(dv1, not(equalTo(dv4)));
        assertThat(dv4, equalTo(dv5));
        assertThat(dv4, not(equalTo(dv6)));
    }
}
