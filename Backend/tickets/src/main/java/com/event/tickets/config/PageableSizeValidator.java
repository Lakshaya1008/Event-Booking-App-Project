package com.event.tickets.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * FIX #11: Global Pagination Security Validator
 *
 * Validates that all Pageable requests respect the configured max page size.
 *
 * BEFORE: spring.data.web.pageable.max-page-size=50 only applied to endpoints
 * with @PageableDefault. Client could bypass by requesting ?size=999999
 * on endpoints without the annotation.
 *
 * AFTER: This resolver intercepts ALL Pageable requests and enforces the limit.
 * If client requests size > max, it's silently capped to max value.
 *
 * Configuration:
 * - spring.data.web.pageable.max-page-size: Hard limit (default: 50)
 * - Requests exceeding this are capped and logged as warnings
 */
@Component
@Slf4j
public class PageableSizeValidator implements HandlerMethodArgumentResolver {

    @Value("${spring.data.web.pageable.max-page-size:50}")
    private int maxPageSize;

    @Value("${spring.data.web.pageable.default-page-size:20}")
    private int defaultPageSize;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(Pageable.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) throws Exception {

        // Get the requested size from query parameter
        String sizeParam = webRequest.getParameter("size");
        String pageParam = webRequest.getParameter("page");

        int requestedSize = defaultPageSize;
        int pageNumber = 0;

        if (sizeParam != null) {
            try {
                requestedSize = Integer.parseInt(sizeParam);
            } catch (NumberFormatException e) {
                log.warn("Invalid page size parameter: {}", sizeParam);
                requestedSize = defaultPageSize;
            }
        }

        if (pageParam != null) {
            try {
                pageNumber = Integer.parseInt(pageParam);
            } catch (NumberFormatException e) {
                log.warn("Invalid page number parameter: {}", pageParam);
                pageNumber = 0;
            }
        }

        // FIX #11: Enforce maximum page size
        if (requestedSize > maxPageSize) {
            log.warn(
                    "FIX #11 AUDIT: Page size exceeded limit. "
                    + "Requested: {}, Max: {}, Path: {}, Client IP: {}",
                    requestedSize,
                    maxPageSize,
                    webRequest.getDescription(false),
                    webRequest.getRemoteUser()
            );
            requestedSize = maxPageSize;
        }

        // Return capped Pageable
        return PageRequest.of(pageNumber, requestedSize);
    }
}

