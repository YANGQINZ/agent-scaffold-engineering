package com.ai.agent.trigger.http;

import com.ai.agent.api.model.agent.*;
import com.ai.agent.domain.agent.model.aggregate.AgentDefinition;
import com.ai.agent.domain.agent.repository.IAgentDefinitionRepository;
import com.ai.agent.domain.agent.service.AgentRegistry;
import com.ai.agent.trigger.converter.AgentDefinitionConverter;
import com.ai.agent.types.model.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Agent定义管理接口 — CRUD操作
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final IAgentDefinitionRepository agentDefinitionRepository;
    private final AgentRegistry agentRegistry;

    /**
     * 查询所有Agent定义
     */
    @GetMapping
    public Response<List<AgentDefinitionDTO>> list() {
        List<AgentDefinition> definitions = agentDefinitionRepository.findAll();
        List<AgentDefinitionDTO> dtos = definitions.stream()
                .map(AgentDefinitionConverter::toDTO)
                .collect(Collectors.toList());
        return Response.buildSuccess(dtos);
    }

    /**
     * 根据agentId查询Agent定义
     */
    @GetMapping("/{agentId}")
    public Response<AgentDefinitionDTO> getByAgentId(@PathVariable String agentId) {
        return agentDefinitionRepository.findByAgentId(agentId)
                .map(definition -> Response.buildSuccess(AgentDefinitionConverter.toDTO(definition)))
                .orElse(Response.buildError("Agent定义不存在: " + agentId));
    }

    /**
     * 创建Agent定义
     */
    @PostMapping
    public Response<AgentDefinitionDTO> create(@Validated @RequestBody AgentDefinitionDTO dto) {
        AgentDefinition definition = AgentDefinitionConverter.toDomain(dto);
        definition.setAgentId(java.util.UUID.randomUUID().toString());
        agentDefinitionRepository.save(definition);
        agentRegistry.register(definition);
        log.info("创建Agent定义: agentId={}, engine={}", definition.getAgentId(), definition.getEngine());
        return Response.buildSuccess(AgentDefinitionConverter.toDTO(definition));
    }

    /**
     * 更新Agent定义
     */
    @PutMapping("/{agentId}")
    public Response<AgentDefinitionDTO> update(@PathVariable String agentId, @Validated @RequestBody AgentDefinitionDTO dto) {
        dto.setAgentId(agentId);
        AgentDefinition definition = AgentDefinitionConverter.toDomain(dto);
        agentDefinitionRepository.update(definition);
        agentRegistry.register(definition);
        log.info("更新Agent定义: agentId={}, engine={}", definition.getAgentId(), definition.getEngine());
        return Response.buildSuccess(AgentDefinitionConverter.toDTO(definition));
    }

    /**
     * 删除Agent定义
     */
    @DeleteMapping("/{agentId}")
    public Response<Void> delete(@PathVariable String agentId) {
        agentDefinitionRepository.deleteByAgentId(agentId);
        agentRegistry.unregister(agentId);
        log.info("删除Agent定义: agentId={}", agentId);
        return Response.buildSuccess();
    }

}
