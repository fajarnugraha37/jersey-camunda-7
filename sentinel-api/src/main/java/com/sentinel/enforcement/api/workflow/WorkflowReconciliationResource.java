package com.sentinel.enforcement.api.workflow;

import com.sentinel.enforcement.api.generated.model.ReconcileWorkflowRequest;
import com.sentinel.enforcement.api.generated.model.WorkflowReconciliationActionResponse;
import com.sentinel.enforcement.api.generated.model.WorkflowReconciliationIssueListResponse;
import com.sentinel.enforcement.api.security.RequestActorResolver;
import com.sentinel.enforcement.api.security.RequestMetadataResolver;
import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.application.workflow.ListWorkflowReconciliationIssuesQuery;
import com.sentinel.enforcement.application.workflow.WorkflowReconciliationApplicationService;
import com.sentinel.enforcement.application.workflow.WorkflowReconciliationPage;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/api/v1/workflow-reconciliation")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public final class WorkflowReconciliationResource {
  private final WorkflowReconciliationApplicationService workflowReconciliationApplicationService;
  private final ApiWorkflowReconciliationMapper mapper = ApiWorkflowReconciliationMapper.INSTANCE;

  @Inject
  public WorkflowReconciliationResource(
      WorkflowReconciliationApplicationService workflowReconciliationApplicationService) {
    this.workflowReconciliationApplicationService = workflowReconciliationApplicationService;
  }

  @GET
  public WorkflowReconciliationIssueListResponse listIssues(
      @QueryParam("cursor") String cursor,
      @QueryParam("q") String quickSearch,
      @QueryParam("searchField") String searchField,
      @QueryParam("searchValue") String searchValue,
      @QueryParam("issueType") String issueType,
      @QueryParam("caseStatus") String caseStatus,
      @QueryParam("workflowCorrelationStatus") String workflowCorrelationStatus,
      @QueryParam("sortBy") String sortBy,
      @QueryParam("sortDirection") String sortDirection,
      @DefaultValue("20") @QueryParam("limit") @Min(1) @Max(50) int limit,
      @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    ListWorkflowReconciliationIssuesQuery query =
        WorkflowReconciliationCursorCodec.decode(
            cursor,
            limit,
            quickSearch,
            searchField,
            searchValue,
            issueType,
            caseStatus,
            workflowCorrelationStatus,
            sortBy,
            sortDirection);
    WorkflowReconciliationPage page =
        workflowReconciliationApplicationService.listIssues(actor, query);
    return mapper.toListResponse(page, WorkflowReconciliationCursorCodec.encode(page, query));
  }

  @POST
  @Path("/{caseId}/actions")
  public WorkflowReconciliationActionResponse reconcileCase(
      @PathParam("caseId") UUID caseId,
      @Valid ReconcileWorkflowRequest request,
      @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    return mapper.toActionResponse(
        workflowReconciliationApplicationService.reconcileCase(
            actor,
            caseId,
            mapper.toActionCommand(
                request,
                RequestMetadataResolver.correlationId(requestContext),
                RequestMetadataResolver.sourceIp(requestContext))));
  }
}
