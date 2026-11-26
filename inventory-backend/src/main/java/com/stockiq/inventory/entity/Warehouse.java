package com.stockiq.inventory.entity;

import java.sql.Timestamp;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Warehouse {
  private long id;
  private Long userId;
  private String code;
  private String name;
  private String description;
  private String address;
  private String city;
  private String state;
  private String postalCode;
  private boolean active;
  private Timestamp createdAt;
  private Timestamp updatedAt;
}

