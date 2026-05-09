package com.example.schemas.rada.messages;

import com.example.schema.annotations.FixedArrayLength;
import com.example.schemas.BaseStruct;
import com.example.schemas.rada.struct.RadaHeader;
import com.example.schemas.rada.struct.RadaPlotData;
import com.example.schemas.rada.struct.RadaTrackData;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RadaTracksExtended implements BaseStruct {

    private RadaHeader header = new RadaHeader();
    private long updateTimeTag;
    private short chunkNumber;
    private short totalNumberIOfReportedTracks;
    private short reserved;
    private short numberOfTracksInThisMessage;
    private short tracksTagYear;
    private short tracksTagMonth;
    private short tracksTagDayOfMonth;
    private short tracksTagHour;
    private short tracksTagMinute;
    private short tracksTagSecond;
    private short tracksTagMillisecond;
    private short reserved1;
    @FixedArrayLength(10)
    private RadaTrackData[] trackData = new RadaTrackData[10];
    @FixedArrayLength(10)
    private RadaPlotData[] plotData = new RadaPlotData[10];

    public RadaTracksExtended() {
        for (int i = 0; i < trackData.length; i++) {
            trackData[i] = new RadaTrackData();
        }
        for (int i = 0; i < plotData.length; i++) {
            plotData[i] = new RadaPlotData();
        }
    }

    public RadaTracksExtended(byte[] message) {
        for (int i = 0; i < trackData.length; i++) {
            trackData[i] = new RadaTrackData();
        }
        for (int i = 0; i < plotData.length; i++) {
            plotData[i] = new RadaPlotData();
        }
        ByteBuffer byteBuffer = ByteBuffer.wrap(message);
        fromByteArray(byteBuffer);
    }

    public RadaHeader getHeader() {
        return header;
    }

    public void setHeader(RadaHeader header) {
        this.header = header;
    }

    public long getUpdateTimeTag() {
        return updateTimeTag;
    }

    public void setUpdateTimeTag(long updateTimeTag) {
        this.updateTimeTag = updateTimeTag;
    }

    public int getChunkNumber() {
        return Short.toUnsignedInt(chunkNumber);
    }

    public void setChunkNumber(int chunkNumber) {
        this.chunkNumber = (short) chunkNumber;
    }

    public int getTotalNumberIOfReportedTracks() {
        return Short.toUnsignedInt(totalNumberIOfReportedTracks);
    }

    public void setTotalNumberIOfReportedTracks(int totalNumberIOfReportedTracks) {
        this.totalNumberIOfReportedTracks = (short) totalNumberIOfReportedTracks;
    }

    public int getReserved() {
        return Short.toUnsignedInt(reserved);
    }

    public void setReserved(int reserved) {
        this.reserved = (short) reserved;
    }

    public int getNumberOfTracksInThisMessage() {
        return Short.toUnsignedInt(numberOfTracksInThisMessage);
    }

    public void setNumberOfTracksInThisMessage(int numberOfTracksInThisMessage) {
        this.numberOfTracksInThisMessage = (short) numberOfTracksInThisMessage;
    }

    public int getTracksTagYear() {
        return Short.toUnsignedInt(tracksTagYear);
    }

    public void setTracksTagYear(int tracksTagYear) {
        this.tracksTagYear = (short) tracksTagYear;
    }

    public int getTracksTagMonth() {
        return Short.toUnsignedInt(tracksTagMonth);
    }

    public void setTracksTagMonth(int tracksTagMonth) {
        this.tracksTagMonth = (short) tracksTagMonth;
    }

    public int getTracksTagDayOfMonth() {
        return Short.toUnsignedInt(tracksTagDayOfMonth);
    }

    public void setTracksTagDayOfMonth(int tracksTagDayOfMonth) {
        this.tracksTagDayOfMonth = (short) tracksTagDayOfMonth;
    }

    public int getTracksTagHour() {
        return Short.toUnsignedInt(tracksTagHour);
    }

    public void setTracksTagHour(int tracksTagHour) {
        this.tracksTagHour = (short) tracksTagHour;
    }

    public int getTracksTagMinute() {
        return Short.toUnsignedInt(tracksTagMinute);
    }

    public void setTracksTagMinute(int tracksTagMinute) {
        this.tracksTagMinute = (short) tracksTagMinute;
    }

    public int getTracksTagSecond() {
        return Short.toUnsignedInt(tracksTagSecond);
    }

    public void setTracksTagSecond(int tracksTagSecond) {
        this.tracksTagSecond = (short) tracksTagSecond;
    }

    public int getTracksTagMillisecond() {
        return Short.toUnsignedInt(tracksTagMillisecond);
    }

    public void setTracksTagMillisecond(int tracksTagMillisecond) {
        this.tracksTagMillisecond = (short) tracksTagMillisecond;
    }

    public int getReserved1() {
        return Short.toUnsignedInt(reserved1);
    }

    public void setReserved1(int reserved1) {
        this.reserved1 = (short) reserved1;
    }

    public RadaTrackData[] getTrackData() {
        return trackData;
    }

    public void setTrackData(RadaTrackData[] trackData) {
        this.trackData = trackData;
    }

    public RadaPlotData[] getPlotData() {
        return plotData;
    }

    public void setPlotData(RadaPlotData[] plotData) {
        this.plotData = plotData;
    }

    @Override
    public void toByteArray(ByteBuffer byteBuffer) {
        header.toByteArray(byteBuffer);
        byteBuffer.putLong(updateTimeTag);
        byteBuffer.putShort(chunkNumber);
        byteBuffer.putShort(totalNumberIOfReportedTracks);
        byteBuffer.putShort(reserved);
        byteBuffer.putShort(numberOfTracksInThisMessage);
        byteBuffer.putShort(tracksTagYear);
        byteBuffer.putShort(tracksTagMonth);
        byteBuffer.putShort(tracksTagDayOfMonth);
        byteBuffer.putShort(tracksTagHour);
        byteBuffer.putShort(tracksTagMinute);
        byteBuffer.putShort(tracksTagSecond);
        byteBuffer.putShort(tracksTagMillisecond);
        byteBuffer.putShort(reserved1);
        for (int i = 0; i < trackData.length; i++) {
            trackData[i].toByteArray(byteBuffer);
        }
        for (int i = 0; i < plotData.length; i++) {
            plotData[i].toByteArray(byteBuffer);
        }
    }

    @Override
    public void fromByteArray(ByteBuffer byteBuffer) {
        byteBuffer.order(ByteOrder.BIG_ENDIAN);
        header.fromByteArray(byteBuffer);
        updateTimeTag = byteBuffer.getLong();
        chunkNumber = byteBuffer.getShort();
        totalNumberIOfReportedTracks = byteBuffer.getShort();
        reserved = byteBuffer.getShort();
        numberOfTracksInThisMessage = byteBuffer.getShort();
        tracksTagYear = byteBuffer.getShort();
        tracksTagMonth = byteBuffer.getShort();
        tracksTagDayOfMonth = byteBuffer.getShort();
        tracksTagHour = byteBuffer.getShort();
        tracksTagMinute = byteBuffer.getShort();
        tracksTagSecond = byteBuffer.getShort();
        tracksTagMillisecond = byteBuffer.getShort();
        reserved1 = byteBuffer.getShort();
        for (int i = 0; i < trackData.length; i++) {
            trackData[i].fromByteArray(byteBuffer);
        }
        for (int i = 0; i < plotData.length; i++) {
            plotData[i].fromByteArray(byteBuffer);
        }
    }
}
