package com.stocker.stocker.domain;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 뉴스 아이템 데이터 모델
 */
public class NewsItem {
    private String category;
    private Long datetime;
    private String headline;
    private Long id;
    private String image;
    private String related;
    private String source;
    private String summary;
    private String url;
    
    public NewsItem() {
    }
    
    // Getters and Setters
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public Long getDatetime() {
        return datetime;
    }
    
    public void setDatetime(Long datetime) {
        this.datetime = datetime;
    }
    
    public String getHeadline() {
        return headline;
    }
    
    public void setHeadline(String headline) {
        this.headline = headline;
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getImage() {
        return image;
    }
    
    public void setImage(String image) {
        this.image = image;
    }
    
    public String getRelated() {
        return related;
    }
    
    public void setRelated(String related) {
        this.related = related;
    }
    
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
    
    public String getSummary() {
        return summary;
    }
    
    public void setSummary(String summary) {
        this.summary = summary;
    }
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    @Override
    public String toString() {
        return "NewsItem{" +
                "id=" + id +
                ", headline='" + headline + '\'' +
                ", datetime=" + datetime +
                ", source='" + source + '\'' +
                '}';
    }
} 