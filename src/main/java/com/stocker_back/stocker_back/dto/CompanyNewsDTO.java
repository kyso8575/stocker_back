package com.stocker_back.stocker_back.dto;

import lombok.Data;

@Data
public class CompanyNewsDTO {
    private String category;
    private Long datetime;
    private String headline;
    private Long id;
    private String image;
    private String related;
    private String source;
    private String summary;
    private String url;
} 