package com.event.tickets.domain.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorDto {
  private String error;
  private String message;
  private int statusCode;
  private String statusDescription;
  private String timestamp;
  private String path;
  private List<String> possibleCauses;
  private List<String> solutions;

  // Constructor for simple error messages (backward compatibility)
  public ErrorDto(String error) {
    this.error = error;
    this.timestamp = LocalDateTime.now().toString();
  }
}
