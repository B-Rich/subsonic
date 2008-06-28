package net.sourceforge.subsonic.controller;

import net.sourceforge.subsonic.domain.Avatar;
import net.sourceforge.subsonic.service.SettingsService;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.web.servlet.mvc.LastModified;
import org.springframework.web.bind.ServletRequestUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Controller which produces avatar images.
 *
 * @author Sindre Mehus
 */
public class AvatarController implements Controller, LastModified {

    private SettingsService settingsService;

    public long getLastModified(HttpServletRequest request) {
        Avatar avatar = getAvatar(request);
        return avatar == null ? -1L : avatar.getCreatedDate().getTime();
    }

    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Avatar avatar = getAvatar(request);

        if (avatar == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }

        // TODO: specify caching filter.

        response.setContentType(avatar.getMimeType());
        response.getOutputStream().write(avatar.getData());
        return null;
    }

    private Avatar getAvatar(HttpServletRequest request) {
        String id = request.getParameter("id");
        if (id != null) {
            return settingsService.getSystemAvatar(Integer.parseInt(id));
        }

        String username = request.getParameter("username");
        if (username != null) {
            return settingsService.getCustomAvatar(username);
        }
        return null;
    }

    public void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }
}