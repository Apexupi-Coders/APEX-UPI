package com.apexupi.psp_switch.config;

import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.pattern.MDCConverter;
import ch.qos.logback.core.filter.Filter;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

@Configuration
public class LogConfig extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String txnId = request.getHeader("X-Txn-Id");
        if (txnId == null) txnId = UUID.randomUUID().toString();
        MDC.put("txnId", txnId);
        filterChain.doFilter(request, response);
        MDC.clear();
    }
}
