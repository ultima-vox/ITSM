package ru.ultimavox.itsm.platform.authorization;
import org.springframework.security.access.AccessDeniedException; import org.springframework.stereotype.Component;
@Component public class AccessControl { private final PermissionChecker permissions; public AccessControl(PermissionChecker permissions){this.permissions=permissions;} public void require(String subject,String permission,String objectType,String objectId){if(!permissions.check(new PermissionChecker.Request(subject,permission,objectType,objectId,null)).allowed()) throw new AccessDeniedException("Permission denied: "+permission);} }
