package com.ai.agent.trigger.http;

import com.ai.agent.api.IKnowledgeBaseService;
import com.ai.agent.api.model.knowledge.dto.KnowledgeBaseResponseDTO;
import com.ai.agent.domain.knowledge.model.entity.KnowledgeBaseResponse;
import com.ai.agent.domain.knowledge.service.IKnowledgeService;
import com.ai.agent.types.enums.OwnerType;
import com.ai.agent.types.model.Response;
import com.ai.agent.types.util.CopyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 知识库 HTTP 控制器 — 通过 IKnowledgeService 接口与业务层交互
 * 仅负责参数校验、HTTP 协议处理和响应封装。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController implements IKnowledgeBaseService {

    private final IKnowledgeService knowledgeService;

    /**
     * 上传知识库文件
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<Void> uploadKnowledgeBase(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "name", required = false) String name) {
        knowledgeService.uploadKnowledgeBase(file, name);
        return Response.buildSuccess();
    }
}
