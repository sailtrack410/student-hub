package com.studenthub.modules.rag.controller.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record RagTestRequest(@NotBlank @Size(max=2000) String question, Boolean generateAnswer) {}
