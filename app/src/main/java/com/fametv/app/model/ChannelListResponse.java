package com.fametv.app.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Root response for the remote channels.json.
 * Schema:
 * {
 *   "version": 1,
 *   "generated_at": "...",
 *   "channel_count": 6723,
 *   "country_count": 169,
 *   "source_repo": "...",
 *   "channels": [ Channel, ... ]
 * }
 */
public class ChannelListResponse {

    @SerializedName("version")
    private int version;

    @SerializedName("generated_at")
    private String generatedAt;

    @SerializedName("channel_count")
    private int channelCount;

    @SerializedName("country_count")
    private int countryCount;

    @SerializedName("source_repo")
    private String sourceRepo;

    @SerializedName("channels")
    private List<Channel> channels;

    public int getVersion() { return version; }
    public String getGeneratedAt() { return generatedAt; }
    public int getChannelCount() { return channelCount; }
    public int getCountryCount() { return countryCount; }
    public String getSourceRepo() { return sourceRepo; }
    public List<Channel> getChannels() { return channels; }
}
