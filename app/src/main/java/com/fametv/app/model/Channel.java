package com.fametv.app.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Single channel record, mapped from the remote channels.json schema.
 */
public class Channel {

    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("country")
    private String country;

    @SerializedName("country_name")
    private String countryName;

    @SerializedName("languages")
    private List<String> languages;

    @SerializedName("is_geo_blocked")
    private boolean geoBlocked;

    @SerializedName("url")
    private String url;          // https://famelack.com/tv/{country}/{id}

    @SerializedName("stream_url")
    private String streamUrl;   // m3u8 URL, may be null

    @SerializedName("youtube_url")
    private String youtubeUrl;  // YouTube embed URL, may be null

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCountry() { return country; }
    public String getCountryName() { return countryName; }
    public List<String> getLanguages() { return languages; }
    public boolean isGeoBlocked() { return geoBlocked; }
    public String getUrl() { return url; }
    public String getStreamUrl() { return streamUrl; }
    public String getYoutubeUrl() { return youtubeUrl; }

    public boolean hasStream() { return streamUrl != null && !streamUrl.isEmpty(); }
    public boolean hasYoutube() { return youtubeUrl != null && !youtubeUrl.isEmpty(); }

    /** Return a display string like "CNA  (Singapore · eng)". */
    public String getDisplaySubtitle() {
        StringBuilder sb = new StringBuilder();
        if (countryName != null && !countryName.isEmpty()) {
            sb.append(countryName);
        } else if (country != null) {
            sb.append(country.toUpperCase());
        }
        if (languages != null && !languages.isEmpty()) {
            sb.append(" · ").append(String.join(", ", languages));
        }
        if (geoBlocked) {
            sb.append(" · GEO");
        }
        return sb.toString();
    }
}
