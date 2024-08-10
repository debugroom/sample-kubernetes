package org.debugroom.sample.kubernetes.servicemesh.app.web.interceptor;

import org.debugroom.sample.kubernetes.servicemesh.app.model.Menu;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

//import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class SetMenuInterceptor implements HandlerInterceptor {

    @Override
    public void postHandle(HttpServletRequest request,
                           HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) throws Exception {
        if(Objects.nonNull(modelAndView)
            && Objects.isNull(modelAndView.getModel().get("menuList"))){
            SecurityContext securityContext = SecurityContextHolder.getContext();
            Authentication authentication = securityContext.getAuthentication();
            if(Objects.nonNull(authentication)
                && authentication.getAuthorities().contains(
                        new SimpleGrantedAuthority("ROLE_ADMIN"))){
                modelAndView.addObject("menuList", getAdminMenuList());
            }else {
                modelAndView.addObject("menuList", getMenuList());
            }
        }

    }

    private List<Menu> getAdminMenuList(){
        return Arrays.asList(Menu.PORTAL, Menu.LOGOUT);
    }

    private List<Menu> getMenuList(){
        return Arrays.asList(Menu.PORTAL, Menu.LOGOUT);
    }

}
