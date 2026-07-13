package com.sentinel.enforcement.api.report;

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
  ReportResponse toResponse(Report report);
}
