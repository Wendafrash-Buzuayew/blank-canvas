package com.qrserve.menu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<CategoryDto> categories;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryDto implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private Long id;
        private String name;
        private List<ProductDto> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductDto implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long id;
        private String name;
        private String description;
        private BigDecimal price;
        private String image;
        private boolean available;
        private Integer preparationTime;
    }
}
