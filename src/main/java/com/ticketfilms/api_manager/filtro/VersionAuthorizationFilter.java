package com.ticketfilms.api_manager.filtro;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

/**
 * Compara la version pedida en el header X-App-Version con los grupos
 * (claim cognito:groups) del JWT.
 *
 * Reglas:
 * - Sin header: la peticion sigue su curso por la ruta por defecto.
 * - Con header: el usuario pasa si es del grupo "admin" o si pertenece
 *   al grupo con el nombre exacto de la version pedida (1.0.0 / 1.1.0).
 * - Si no coincide → 403. Sin token → 401 (lo resuelve la seguridad del
 *   gateway, que corre antes que los filtros del propio gateway: si el
 *   token no fuera valido la peticion nunca llegaria hasta aqui).
 */
@Component
public class VersionAuthorizationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(VersionAuthorizationFilter.class);
    private static final String HEADER_VERSION = "X-App-Version";
    private static final String GRUPO_ADMIN = "admin";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String version = exchange.getRequest().getHeaders().getFirst(HEADER_VERSION);
        if (version == null || version.isBlank()) {
            return chain.filter(exchange);
        }

        String bearer = exchange.getRequest().getHeaders().getFirst("Authorization");
        List<String> grupos = leerGrupos(bearer);

        boolean autorizado = grupos != null
                && (grupos.contains(GRUPO_ADMIN) || grupos.contains(version));

        if (!autorizado) {
            log.warn("403 VERSION - version pedida={} grupos={}", version, grupos);
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }

        log.info("VERSION OK - version pedida={} grupos={}", version, grupos);
        return chain.filter(exchange);
    }

    private List<String> leerGrupos(String bearer) {
        try {
            if (bearer == null || !bearer.startsWith("Bearer ")) {
                return null;
            }
            String payload = bearer.substring(7).split("\\.")[1];
            String json = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
            JsonNode nodo = objectMapper.readTree(json).path("cognito:groups");
            if (nodo.isArray()) {
                return objectMapper.convertValue(nodo,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            }
        } catch (Exception e) {
            log.warn("No se pudo leer cognito:groups del JWT: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
