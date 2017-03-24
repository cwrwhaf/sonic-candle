package com.dakkra.wav;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * @author Chris Soderquist(Dakkra)
 *         email: contact@dakkra.com
 *         web: http://dakkra.com
 *         <p>
 *         A home made wevfile decoder for use with sonic candle
 *         Supports RIFF PCM only
 *         The host is that this will provide the flexibility we need to use different bit rates
 *         This should also allow us to regulate the type of wav files we will support
 *         I plan to make this more human readable than the labbookpages decoder
 *         Note: There will not be an encoder as we never write out wave files directly
 *         The jLayer encoder will  have to be investigated later
 *         <p>
 *         Based on the specifiction provided by http://soundfile.sapp.org/doc/WaveFormat/
 */
public class WavFile {
    //wave file constants
    //"RIFF" in ascii
    private static final int RIFF_CHUNK_ID = 0x52494646;
    //"WAVE" in ascii
    private static final int WAVE_FORMAT = 0x57415645;
    //"fmt" in ascii
    private static final int SUBCHUNK1ID = 0x666d7420;
    //PCM uses 16. That's the only wave we will support. Read as little endian
    private static final int SUBCHUNK1SIZE = LittleEndianInt.getBigEndianFromInt(16);
    //Format = 1 for pcm. Others mean compression
    private static final int AUDIO_FORMAT = 1;
    //"data" in ascii
    private static final int SUBCHUNK2ID = 0x64617461;
    //Data should start at byte 44
    private static final int DATA_START_OFFSET = 44;

    //Wave file header
    //RIFF section
    private int chunkID;
    private LittleEndianInt chunkSize;
    private int format;
    //fmt section
    private int subchunk1ID;
    private LittleEndianInt subchunk1Size;
    private LittleEndianShort audioFormat;
    private LittleEndianShort numChannels;
    private LittleEndianInt samplerate;
    private LittleEndianInt byteRate;
    private LittleEndianShort blockAlign;
    private LittleEndianShort bitsPerSample;
    //Data header
    private int subchunk2ID;
    private LittleEndianInt subchunk2Size;

    //Input stream
    private InputStream inputStream;

    public WavFile(File inputFile) {
        try {
            inputStream = new FileInputStream(inputFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.err.println("Failed to get input stream for file");
        }
    }

    public WavFile(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    /**
     * Opens wav file stream.
     * Must require RIFF PCM format. Others not supported yet
     *
     * @return Returns false if failed read or incorrect wave data
     * @throws Exception
     */
    public boolean open() throws Exception {
        return readHeader();
    }

    private boolean readHeader() throws Exception {
        //Declare buffers for use in storing bytes read in form inputStream
        byte intByteBuffer[] = new byte[4];
        byte shortByteBuffer[] = new byte[2];

        //ChunkID
        inputStream.read(intByteBuffer);
        chunkID = bytesToInt(intByteBuffer);
        //Check to see if valid RIFF file
        if (chunkID != RIFF_CHUNK_ID) {
            throw new WavFileException("NOT A VALID RIFF FILE");
        }

        //ChunkSize
        inputStream.read(intByteBuffer);
        chunkSize = new LittleEndianInt(bytesToInt(intByteBuffer));

        //Format
        inputStream.read(intByteBuffer);
        format = bytesToInt(intByteBuffer);
        if (format != WAVE_FORMAT)
            throw new WavFileException("INVALID WAV FORMAT");


        //Subchunk1ID
        inputStream.read(intByteBuffer);
        subchunk1ID = bytesToInt(intByteBuffer);
        if (subchunk1ID != SUBCHUNK1ID)
            throw new WavFileException("INVALID SUBCHUNK 1 ID");


        //SubChunk1Size
        inputStream.read(intByteBuffer);
        subchunk1Size = new LittleEndianInt(bytesToInt(intByteBuffer));
        if (subchunk1Size.convert() != SUBCHUNK1SIZE)
            throw new WavFileException("NON PCM FILES ARE UNSUPPORTED");


        //Audio Format
        inputStream.read(shortByteBuffer);
        audioFormat = new LittleEndianShort(bytesToShort(shortByteBuffer));
        if (audioFormat.convert() != AUDIO_FORMAT)
            throw new WavFileException("COMPRESSED WAVE FILE NOT SUPPORTED");


        //NumChannels
        inputStream.read(shortByteBuffer);
        numChannels = new LittleEndianShort(bytesToShort(shortByteBuffer));
        if (numChannels.convert() > 2 || numChannels.convert() < 0)
            throw new WavFileException("INVALID NUMBER OF CHANNELS");

        //SampleRate
        inputStream.read(intByteBuffer);
        samplerate = new LittleEndianInt(bytesToInt(intByteBuffer));

        //ByteRate
        inputStream.read(intByteBuffer);
        byteRate = new LittleEndianInt(bytesToInt(intByteBuffer));

        //BlockAlign
        inputStream.read(shortByteBuffer);
        blockAlign = new LittleEndianShort(bytesToShort(shortByteBuffer));

        //BitsPerSample
        //We only support 16, 24, and 32
        inputStream.read(shortByteBuffer);
        bitsPerSample = new LittleEndianShort(bytesToShort(shortByteBuffer));

        //SubChunk2ID
        inputStream.read(intByteBuffer);
        subchunk2ID = bytesToInt(intByteBuffer);
        if (subchunk2ID != SUBCHUNK2ID)
            throw new WavFileException("INVALID DATA HEADER");

        //Subchunk2Size
        inputStream.read(shortByteBuffer);
        subchunk2Size = new LittleEndianInt(bytesToShort(shortByteBuffer));

        //Check chunk sizes for errors
        int theory = samplerate.convert() * bitsPerSample.convert() / 8;
        if (subchunk2Size.convert() != theory)
            throw new WavFileException("INVALID SUB CHUNK 2 SIZE");

        if (subchunk1Size.convert() != (theory + 36))
            throw new WavFileException("INVALID SUB CHUNK 1 SIZE");
        
        //Everything loaded fine
        return true;
    }

    public int getFileSize() {
        return subchunk1Size.convert() + 8;
    }

    public int bytesToInt(byte bytes[]) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        bb.order(ByteOrder.BIG_ENDIAN);
        return bb.getInt();
    }

    public short bytesToShort(byte bytes[]) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        bb.order(ByteOrder.BIG_ENDIAN);
        return bb.getShort();
    }
}
