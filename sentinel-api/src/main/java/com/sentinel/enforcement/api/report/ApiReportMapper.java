package com.sentinel.enforcement.api.report;

import com.sentinel.enforcement.api.generated.model.CreateReportRequest;
import com.sentinel.enforcement.api.generated.model.ReportResponse;
import com.sentinel.enforcement.application.report.CreateReportCommand;
import com.sentinel.enforcement.domain.report.Report;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ApiReportMapper {
  ApiReportMapper INSTANCE = Mappers.getMapper(ApiReportMapper.class);

  @Mapping(target = "actorId", source = "actorId")
  CreateReportCommand toCommand(CreateReportRequest request, String actorId);

  @Mapping(target = "status", expression = "java(report.status().name())")
  @Mapping(
      target = "createdAt",
      expression =
          "java(java.time.OffsetDateTime.ofInstant(report.createdAt(), java.time.ZoneOffset.UTC))")
  @Mapping(
      target = "updatedAt",
      expression =
          "java(java.time.OffsetDateTime.ofInstant(report.updatedAt(), java.time.ZoneOffset.UTC))")
  ReportResponse toResponse(Report report);
}
