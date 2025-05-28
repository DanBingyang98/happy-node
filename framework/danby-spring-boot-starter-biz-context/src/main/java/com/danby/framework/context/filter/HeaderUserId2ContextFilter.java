package com.danby.framework.context.filter;

import com.danby.framework.context.holder.LoginUserContextHolder;
import com.danby.happynode.framework.common.constant.GlobalConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
public class HeaderUserId2ContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        // 从请求头中获取用户 ID
        String userId = request.getHeader(GlobalConstants.USER_ID);

        if (StringUtils.isNotBlank(userId)) {
            log.info("===== 设置 userId 到 ThreadLocal 中， 用户 ID: {}", userId);
            LoginUserContextHolder.setUserId(userId);
            try {
                filterChain.doFilter(request, response);
            } finally {
                // 一定要删除 ThreadLocal ，防止内存泄露
                LoginUserContextHolder.remove();
                log.info("===== 删除 ThreadLocal， userId: {}", userId);
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }
}
