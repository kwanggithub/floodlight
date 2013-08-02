package net.bigdb.data;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * DigestValue is a wrapper class for the digested bytes returned from a digest
 * algorithm like MD5 or SHA-1 (it currently uses SHA-1). It has a builder
 * object to support incrementally digesting a sequence of byte arrays and/or
 * strings to obtain the final digest value.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public final class DigestValue {

    public static class Builder {

        /** The digest object used to perform the digest operation */
        private MessageDigest digest;

        /** Which digest algorithm to use */
        private static final String DIGEST_ALGORITHM = "SHA-1";

        /** Which character encoding to use to convert strings to bytes when digesting strings */
        private static final String CHARACTER_ENCODING_NAME = "UTF-8";

        public Builder() {
            try {
                // FIXME: It's not super efficient to create a new digest
                // instance for every digest value that's computed. In a simple
                // benchmark this incurred roughly a 4x performance penalty.
                // If necessary, this could be optimized by keeping a pool of
                // MessageDigest objects.
                digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            }
            catch (NoSuchAlgorithmException e) {
                // SHA-1 should always be available, so this should never happen
                throw new UnsupportedOperationException(
                        "Unknown digest algorithm: " + DIGEST_ALGORITHM);
            }
        }

        /**
         * Update the digest with more bytes.
         *
         * @param bytes the bytes to digest
         * @return
         */
        public Builder update(byte[] bytes) {
            digest.update(bytes);
            return this;
        }

        /**
         * Update the digest with a string.
         *
         * @param string the string to digest
         * @return
         */
        public Builder update(String string) {
            try {
                byte[] bytes = string.getBytes(CHARACTER_ENCODING_NAME);
                update(bytes);
                return this;
            }
            catch (UnsupportedEncodingException e) {
                throw new UnsupportedOperationException(
                        "Unknown character encoding: " + CHARACTER_ENCODING_NAME);
            }
        }

        /**
         * @return the current digest value
         */
        public DigestValue getDigestValue() {
            return DigestValue.fromDigestBytes(digest.digest());
        }
    }

    /** The low-level byte array returned from the MessageDigest object */
    private final byte[] digestBytes;


    private DigestValue(byte[] digestBytes) {
        if (digestBytes == null)
            throw new IllegalArgumentException("Digest bytes must be non-null");
        this.digestBytes = digestBytes;
    }

    /**
     * Factory method to create a digest value from the specified digest bytes.
     * Note that these are the bytes produced by the digest algorithm not the
     * bytes to be input to the digest algorithm. Use fromBytes instead if you
     * want to feed the bytes into the digest algorithm.
     *
     * @param digestBytes the low-level digest byte array value
     * @return the digested value
     */
    public static DigestValue fromDigestBytes(byte[] digestBytes) {
        return new DigestValue(digestBytes);
    }

    /**
     * Factory method to create a digest value by digesting the specified bytes.
     *
     * @param bytes the bytes to be digested
     * @return the digested value
     */
    public static DigestValue fromBytes(byte[] bytes) {
        Builder builder = new Builder();
        builder.update(bytes);
        return builder.getDigestValue();
    }

    /**
     * Factory method to create a digest value by digesting the specified string.
     *
     * @param string the string to be digested
     * @return the digested value
     */
    public static DigestValue fromString(String string) {
        Builder builder = new Builder();
        builder.update(string);
        return builder.getDigestValue();
    }

    /**
     * Obtain the low-level byte array digest value.
     * Note: this returns a copy of the digest bytes, so modifying the returned
     * array does not change the state of the source DigestValue instance.
     *
     * @return the low-level digest byte array
     */
    public byte[] getDigestBytes() {
        // To be safe return a copy of the bytes to preserve the immutability
        // of the DigestValue object. Otherwise the caller could call
        // getDigestBytes and modify the byte array.
        return digestBytes.clone();
    }

    static final char[] HEX_DIGITS = {
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    public String toString() {
        char[] digestChars = new char[digestBytes.length * 2];
        for (int i = 0; i < digestBytes.length; i++) {
            int digit = digestBytes[i] & 0xFF;
            digestChars[i * 2] = HEX_DIGITS[digit >>> 4];
            digestChars[i * 2 + 1] = HEX_DIGITS[digit & 0x0F];
        }
        return new String(digestChars);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(digestBytes);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DigestValue other = (DigestValue) obj;
        if (!Arrays.equals(digestBytes, other.digestBytes))
            return false;
        return true;
    }
}
