package com.ai.agent.trigger.http;

import com.ai.agent.api.IMcpServerConfigService;
import com.ai.agent.api.model.agent.McpServerConfigDTO;
import com.ai.agent.domain.agent.model.entity.McpServerConfig;
import com.ai.agent.domain.agent.repository.IMcpServerConfigRepository;
import com.ai.agent.types.model.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MCP Server 配置管理 HTTP 控制器
 *
 * 提供独立 MCP 配置的 CRUD 接口，供前端管理页面和节点属性面板使用。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/mcp")
@RequiredArgsConstructor
public class McpServerConfigController implements IMcpServerConfigService {

    private final IMcpServerConfigRepository mcpServerConfigRepository;

    /**
     * 查询所有 MCP 配置
     */
    @Override
    @GetMapping
    public Response<List<McpServerConfigDTO>> listMcpConfigs() {
        List<McpServerConfigDTO> configs = mcpServerConfigRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
        return Response.buildSuccess(configs);
    }

    /**
     * 创建 MCP 配置
     */
    @Override
    @PostMapping
    public Response<McpServerConfigDTO> createMcpConfig(@RequestBody McpServerConfigDTO dto) {
        // name 唯一性校验
        if (mcpServerConfigRepository.findByName(dto.getName()).isPresent()) {
            return Response.buildError("MCP配置名称已存在: " + dto.getName());
        }

        McpServerConfig config = toDomain(dto);
        IMcpServerConfigRepository.McpServerConfigEntry entry =
                mcpServerConfigRepository.save(config, dto.getDescription());
        return Response.buildSuccess(toDTO(entry));
    }

    /**
     * 更新 MCP 配置
     */
    @Override
    @PutMapping("/{id}")
    public Response<Void> updateMcpConfig(@PathVariable("id") Long id,
                                          @RequestBody McpServerConfigDTO dto) {
        if (mcpServerConfigRepository.findById(id).isEmpty()) {
            return Response.buildError("MCP配置不存在: id=" + id);
        }

        // name 唯一性校验（排除自身）
        mcpServerConfigRepository.findByName(dto.getName()).ifPresent(existing -> {
            if (!existing.id().equals(id)) {
                throw new IllegalArgumentException("MCP配置名称已被占用: " + dto.getName());
            }
        });

        McpServerConfig config = toDomain(dto);
        mcpServerConfigRepository.update(id, config, dto.getDescription());
        return Response.buildSuccess();
    }

    /**
     * 删除 MCP 配置
     */
    @Override
    @DeleteMapping("/{id}")
    public Response<Void> deleteMcpConfig(@PathVariable("id") Long id) {
        mcpServerConfigRepository.deleteById(id);
        return Response.buildSuccess();
    }

    // ═══════════════════════════════════════════════════════
    // DTO 转换
    // ═══════════════════════════════════════════════════════

    private McpServerConfig toDomain(McpServerConfigDTO dto) {
        return McpServerConfig.builder()
                .name(dto.getName())
                .transport(dto.getTransport())
                .command(dto.getCommand())
                .args(dto.getArgs())
                .url(dto.getUrl())
                .headers(dto.getHeaders())
                .build();
    }

    private McpServerConfigDTO toDTO(IMcpServerConfigRepository.McpServerConfigEntry entry) {
        return McpServerConfigDTO.builder()
                .id(entry.id())
                .name(entry.name())
                .transport(entry.transport())
                .command(entry.command())
                .args(entry.args())
                .url(entry.url())
                .headers(entry.headers())
                .description(entry.description())
                .build();
    }

}
