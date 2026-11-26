package com.stockiq.inventory.common.enums.stock;

import lombok.Getter;

@Getter
public enum MovementType {
  IN(1),
  OUT(0),
  STOCK_IN(1),
  STOCK_OUT(0),
  ADJUSTMENT(2);

  private final int value;

  MovementType(int value) {
    this.value = value;
  }

  public static MovementType fromValue(int value) {
    for (MovementType type : values()) {
      if (type.getValue() == value) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown MovementType value: " + value);
  }

  public static MovementType fromString(String name) {
    if (name == null) {
      throw new IllegalArgumentException("MovementType cannot be null");
    }
    String upper = name.trim().toUpperCase();
    return switch (upper) {
      case "IN", "STOCK_IN" -> STOCK_IN;
      case "OUT", "STOCK_OUT" -> STOCK_OUT;
      case "ADJUSTMENT", "ADJUST" -> ADJUSTMENT;
      default -> throw new IllegalArgumentException("Unknown MovementType name: " + name);
    };
  }
}
