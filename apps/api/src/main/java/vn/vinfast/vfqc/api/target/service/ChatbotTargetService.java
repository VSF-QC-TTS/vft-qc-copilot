package vn.vinfast.vfqc.api.target.service;

import java.util.List;
import java.util.UUID;
import vn.vinfast.vfqc.api.common.security.CurrentUser;
import vn.vinfast.vfqc.api.target.dto.ChatbotTargetExampleResponse;
import vn.vinfast.vfqc.api.target.dto.ChatbotTargetReadinessResponse;
import vn.vinfast.vfqc.api.target.dto.ChatbotTargetResponse;
import vn.vinfast.vfqc.api.target.dto.CreateChatbotTargetRequest;
import vn.vinfast.vfqc.api.target.dto.ResponseExtractionConfigDto;
import vn.vinfast.vfqc.api.target.dto.TestChatbotTargetRequest;
import vn.vinfast.vfqc.api.target.dto.UpdateChatbotTargetRequest;

public interface ChatbotTargetService {

  /**
   * Lists non-deleted chatbot targets for a project after verifying project view permission.
   *
   * @param user authenticated actor
   * @param projectId owning project id
   * @return targets ordered by newest creation time
   */
  List<ChatbotTargetResponse> list(CurrentUser user, UUID projectId);

  /**
   * Creates a draft chatbot target after validating project edit permission, URL, secret bindings,
   * dataset mappings, and sanitized request config. Raw cURL is sanitized before persistence.
   *
   * @param user authenticated actor
   * @param projectId owning project id
   * @param request target create payload
   * @return created target response
   */
  ChatbotTargetResponse create(CurrentUser user, UUID projectId, CreateChatbotTargetRequest request);

  /**
   * Loads one non-deleted target after checking project visibility.
   *
   * @param user authenticated actor
   * @param targetId target id
   * @return target response
   */
  ChatbotTargetResponse get(CurrentUser user, UUID targetId);

  /**
   * Updates metadata, request config, extraction config, and optional status transition after edit
   * permission checks. Plaintext secrets are never persisted.
   *
   * @param user authenticated actor
   * @param targetId target id
   * @param request patch payload
   * @return updated target response
   */
  ChatbotTargetResponse update(CurrentUser user, UUID targetId, UpdateChatbotTargetRequest request);

  /**
   * Soft-deletes a target after project edit permission. Deleted targets are no longer selectable or
   * testable.
   *
   * @param user authenticated actor
   * @param targetId target id
   */
  void delete(CurrentUser user, UUID targetId);

  /**
   * Executes a target test request with runtime secret resolution, persists a sanitized example, and
   * updates the latest example pointer.
   *
   * @param user authenticated actor
   * @param targetId target id
   * @param request optional dataset sample values
   * @return persisted sanitized example
   */
  ChatbotTargetExampleResponse test(CurrentUser user, UUID targetId, TestChatbotTargetRequest request);

  /**
   * Lists sanitized examples for a target after project view permission.
   *
   * @param user authenticated actor
   * @param targetId target id
   * @return sanitized examples ordered newest first
   */
  List<ChatbotTargetExampleResponse> examples(CurrentUser user, UUID targetId);

  /**
   * Replaces response extraction config after validating project edit permission and config shape.
   *
   * @param user authenticated actor
   * @param targetId target id
   * @param config extraction config
   * @return updated target response
   */
  ChatbotTargetResponse updateExtraction(
      CurrentUser user, UUID targetId, ResponseExtractionConfigDto config);

  /**
   * Returns readiness blockers for a target after project view permission.
   *
   * @param user authenticated actor
   * @param targetId target id
   * @return readiness result
   */
  ChatbotTargetReadinessResponse readiness(CurrentUser user, UUID targetId);
}
