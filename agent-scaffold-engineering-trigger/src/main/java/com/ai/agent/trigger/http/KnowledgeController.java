package com.ai.agent.trigger.http;

import com.ai.agent.api.IKnowledgeService;
import com.ai.agent.api.model.knowledge.KnowledgeBaseResponseDTO;
import com.ai.agent.types.common.Constants;
import com.ai.agent.types.enums.OwnerType;
import com.ai.agent.types.model.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库 HTTP 控制器 — 通过 IKnowledgeService 接口与业务层交互
 * 仅负责参数校验、HTTP 协议处理和响应封装。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final IKnowledgeService knowledgeService;

    @PostMapping("/bases")
    public Response<KnowledgeBaseResponseDTO> createKnowledgeBase(
            @RequestParam String name,
            @RequestParam(defaultValue = "") String description,
            @RequestParam(defaultValue = "USER") String ownerType,
            @RequestParam(defaultValue = "") String ownerId) {
        // 参数校验：name 不能为空或空白
        if (name == null || name.isBlank()) {
            return Response.<KnowledgeBaseResponseDTO>builder()
                    .code(Constants.ResponseCode.UN_ERROR.getCode())
                    .info("知识库名称不能为空")
                    .build();
        }

        // 参数校验：ownerType 必须是合法的枚举值
        try {
            OwnerType.valueOf(ownerType);
        } catch (IllegalArgumentException e) {
            return Response.<KnowledgeBaseResponseDTO>builder()
                    .code(Constants.ResponseCode.UN_ERROR.getCode())
                    .info("无效的ownerType: " + ownerType + "，合法值: ADMIN, USER")
                    .build();
        }

        try {
            KnowledgeBaseResponseDTO data = knowledgeService.createKnowledgeBase(name, description, ownerType, ownerId);
            return Response.<KnowledgeBaseResponseDTO>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(data)
                    .build();
        } catch (Exception e) {
            log.error("创建知识库失败: name={}, error={}", name, e.getMessage(), e);
            return Response.<KnowledgeBaseResponseDTO>builder()
                    .code(Constants.ResponseCode.UN_ERROR.getCode())
                    .info("创建知识库失败: " + e.getMessage())
                    .build();
        }
    }

    @PostMapping("/upload")
    public Response<String> uploadDocument(
            @RequestParam String knowledgeBaseId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String userId) {
        try {
            String docId = knowledgeService.uploadDocument(knowledgeBaseId, file, userId);
            return Response.<String>builder()
                    .code(Constants.ResponseCode.SUCCESS.getCode())
                    .info(Constants.ResponseCode.SUCCESS.getInfo())
                    .data(docId)
                    .build();
        } catch (Exception e) {
            log.error("文档上传处理失败: baseId={}, error={}", knowledgeBaseId, e.getMessage(), e);
            return Response.<String>builder()
                    .code(Constants.ResponseCode.UN_ERROR.getCode())
                    .info("文档处理失败: " + e.getMessage())
                    .data(null)
                    .build();
        }
    }
}
