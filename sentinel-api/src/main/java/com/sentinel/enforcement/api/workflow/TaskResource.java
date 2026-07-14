package com.sentinel.enforcement.api.workflow;

import com.sentinel.enforcement.api.generated.model.WorkflowTaskListResponse;
import com.sentinel.enforcement.api.generated.model.WorkflowTaskResponse;
import com.sentinel.enforcement.api.security.RequestActorResolver;
import com.sentinel.enforcement.api.security.RequestMetadataResolver;
import com.sentinel.enforcement.application.security.ApplicationActor;
import com.sentinel.enforcement.application.workflow.ListWorkflowTasksQuery;
import com.sentinel.enforcement.application.workflow.WorkflowTaskApplicationService;
import com.sentinel.enforcement.application.workflow.WorkflowTaskPage;
import jakarta.inject.Inject;
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
import jakarta.ws.rs.core.Response;

@Path("/api/v1/tasks")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public final class TaskResource {
  private final WorkflowTaskApplicationService workflowTaskApplicationService;
  private final ApiWorkflowTaskMapper mapper = ApiWorkflowTaskMapper.INSTANCE;

  @Inject
  public TaskResource(WorkflowTaskApplicationService workflowTaskApplicationService) {
    this.workflowTaskApplicationService = workflowTaskApplicationService;
  }

  @GET
  public WorkflowTaskListResponse listTasks(
      @QueryParam("cursor") String cursor,
      @QueryParam("q") String quickSearch,
      @QueryParam("searchField") String searchField,
      @QueryParam("searchValue") String searchValue,
      @QueryParam("caseId") String caseId,
      @QueryParam("assigneeUserId") String assigneeUserId,
      @QueryParam("state") String state,
      @QueryParam("sortBy") String sortBy,
      @QueryParam("sortDirection") String sortDirection,
      @DefaultValue("20") @QueryParam("limit") @Min(1) @Max(50) int limit,
      @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    ListWorkflowTasksQuery query =
        TaskCursorCodec.decode(
            cursor,
            limit,
            quickSearch,
            searchField,
            searchValue,
            caseId,
            assigneeUserId,
            state,
            sortBy,
            sortDirection);
    WorkflowTaskPage taskPage = workflowTaskApplicationService.listTasks(actor, query);
    return mapper.toListResponse(taskPage, TaskCursorCodec.encode(taskPage, query));
  }

  @POST
  @Path("/{taskId}/claim")
  public WorkflowTaskResponse claimTask(
      @PathParam("taskId") String taskId, @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    return mapper.toResponse(
        workflowTaskApplicationService.claimTask(
            actor,
            taskId,
            RequestMetadataResolver.correlationId(requestContext),
            RequestMetadataResolver.sourceIp(requestContext)));
  }

  @POST
  @Path("/{taskId}/complete")
  public Response completeTask(
      @PathParam("taskId") String taskId, @Context ContainerRequestContext requestContext) {
    ApplicationActor actor = RequestActorResolver.resolveRequired(requestContext);
    workflowTaskApplicationService.completeTask(
        actor,
        taskId,
        RequestMetadataResolver.correlationId(requestContext),
        RequestMetadataResolver.sourceIp(requestContext));
    return Response.noContent().build();
  }
}
