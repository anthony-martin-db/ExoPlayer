package com.google.android.exoplayer2.source.rtsp.reader;

import androidx.annotation.IntDef;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.rtsp.RtpPayloadFormat;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Decodes ITU-T G.711, G.711.1 audio, both a-law and mu-law.
 */
/* package */ final class RtpG711Reader implements RtpPayloadReader {

  private @MonotonicNonNull TrackOutput trackOutput;

  private long firstReceivedTimestamp;
  private long startTimeOffsetUs;
  private final RtpPayloadFormat payloadFormat;

  public RtpG711Reader(RtpPayloadFormat payloadFormat) {
    this.payloadFormat = payloadFormat;
  }

  @Override
  public void seek(long nextRtpTimestamp, long timeUs) {
    firstReceivedTimestamp = nextRtpTimestamp;
    startTimeOffsetUs = timeUs;
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, int trackId) {
    trackOutput = extractorOutput.track(trackId, C.TRACK_TYPE_AUDIO);
    trackOutput.format(payloadFormat.format);
  }

  @Override
  public void onReceivingFirstPacket(long timestamp, int sequenceNumber) {
    firstReceivedTimestamp = timestamp;
  }

  @Override
  public void consume(ParsableByteArray packet, long timestamp, int sequenceNumber, boolean rtpMarker) {

    long sampleTimeUs = toSampleTimeUs(startTimeOffsetUs, timestamp, firstReceivedTimestamp,
        payloadFormat.clockRate);

    if (sampleTimeUs < 0) {
      packet.skipBytes(packet.bytesLeft());
      return;
    }

    int limit = packet.bytesLeft();
    // Write the audio sample
    trackOutput.sampleData(packet, limit);
    trackOutput.sampleMetadata(sampleTimeUs, C.BUFFER_FLAG_KEY_FRAME, limit,
        /* offset= */ 0, /* cryptoData= */ null);
  }

  // handle and output samples for G.711.0
  private void handleV0Samples(ParsableByteArray packet, long sampleTimeUs) {
    int limit = packet.bytesLeft();
    // Write the audio sample
    trackOutput.sampleData(packet, limit);
    trackOutput.sampleMetadata(sampleTimeUs, C.BUFFER_FLAG_KEY_FRAME, limit,
        0, null);
  }

  /** Returns the correct sample time from RTP timestamp, accounting for the AAC sampling rate. */
  private static long toSampleTimeUs(
      long startTimeOffsetUs, long rtpTimestamp, long firstReceivedRtpTimestamp, int sampleRate) {
    return startTimeOffsetUs
        + Util.scaleLargeTimestamp(
        rtpTimestamp - firstReceivedRtpTimestamp,
        /* multiplier= */ C.MICROS_PER_SECOND,
        /* divisor= */ sampleRate);
  }
}
