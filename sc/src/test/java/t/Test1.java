package t;

/**
 * @author liuyu
 * @date 2023/11/27
 */
public class Test1 {
    public static void main(String[] args) {
        byte[] originalArray = new byte[103];
        for (int i = 0; i < originalArray.length; i++) {
            originalArray[i] = (byte) i;
        }
        int maxChunkSize = 10;
        byte[][] r = splitByteArray(originalArray,maxChunkSize);
        System.out.println(r);
    }

    private static byte[][] splitByteArray(byte[] originalArray, int maxChunkSize) {
        int length = originalArray.length;
        int numOfChunks = (int) Math.ceil((double) length / maxChunkSize);
        byte[][] splitArrays = new byte[numOfChunks][];

        for (int i = 0; i < numOfChunks; i++) {
            int start = i * maxChunkSize;
            int end = Math.min((i + 1) * maxChunkSize, length);
            int chunkSize = end - start;

            byte[] chunk = new byte[chunkSize];
            System.arraycopy(originalArray, start, chunk, 0, chunkSize);

            splitArrays[i] = chunk;
        }

        return splitArrays;
    }
}
