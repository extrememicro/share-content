/*
 * Copyright (C) 2003-2014 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.ecm.webui.component.explorer.popup.service;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.exoplatform.container.PortalContainer;
import org.exoplatform.ecm.webui.utils.PermissionUtil;
import org.exoplatform.services.cms.drives.ManageDriveService;
import org.exoplatform.services.cms.link.LinkManager;
import org.exoplatform.services.cms.link.NodeFinder;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.ExtendedNode;
import org.exoplatform.services.jcr.core.ManageableRepository;
import org.exoplatform.services.jcr.ext.app.SessionProviderService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.wcm.core.NodetypeConstant;
import org.exoplatform.services.wcm.utils.WCMCoreUtils;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.IdentityManager;


/**
 * Created by The eXo Platform SAS
 * Author : eXoPlatform
 *          exo@exoplatform.com
 * Nov 19, 2014  
 */
public class ShareDocumentService implements IShareDocumentService {
  private static final Log    LOG                 = ExoLogger.getLogger(ShareDocumentService.class);
  private RepositoryService repoService;
  private SessionProviderService sessionProviderService;
  private LinkManager linkManager;
  public ShareDocumentService(RepositoryService _repoService,
                              LinkManager _linkManager,
                              IdentityManager _identityManager,
                              SessionProviderService _sessionProviderService,
                              ManageDriveService _driveService){
    this.repoService = _repoService;
    this.sessionProviderService = _sessionProviderService;
    this.linkManager = _linkManager;
  }
  public Node getNodeByPath(String repoName,String nodePath){
    try {
      NodeFinder finder = (NodeFinder) PortalContainer.getInstance().getComponentInstance(NodeFinder.class);
      SessionProvider sessionProvider = sessionProviderService.getSystemSessionProvider(null);
      ManageableRepository repository = repoService.getCurrentRepository();
      Session session = sessionProvider.getSession(repository.getConfiguration().getDefaultWorkspaceName(), repository);
      return (Node) finder.getItem(session, nodePath);
    } catch (RepositoryException e) {
      LOG.error(e.getMessage(), e);
    }
    return null;
  }
  /* (non-Javadoc)
   * @see org.exoplatform.ecm.webui.component.explorer.popup.service.IShareDocumentService#publicDocumentToSpace(java.lang.String, javax.jcr.Node, java.lang.String, java.lang.String)
   */
  @Override
  public boolean publicDocumentToSpace(String space, Node node, String comment,String perm) {
    Node rootSpace = null;
    Node shared = null;
    try {
      SessionProvider sessionProvider = sessionProviderService.getSystemSessionProvider(null);
      ManageableRepository repository = repoService.getCurrentRepository();
      Session session = sessionProvider.getSession(repository.getConfiguration().getDefaultWorkspaceName(), repository);
      //add symlink to destination space
      rootSpace = session.getRootNode().getNode("Groups").getNode("spaces").getNode(space);
      if(!rootSpace.hasNode("Shared")){
        shared = rootSpace.addNode("Shared");
      }else{
        shared = rootSpace.getNode("Shared");
      }
      Node link = linkManager.createLink(shared, node);
      rootSpace.save();
      //Update permission 
      String tempPerms = perm.toString();//Avoid ref back to UIFormSelectBox options
      if(tempPerms.equals("modify")) tempPerms = "read,add_node,set_property";
      if(PermissionUtil.canChangePermission(node)){
        node.addMixin("exo:privilegeable");
        ExtendedNode enode = (ExtendedNode) node;
        enode.setPermission("*:/spaces/" + space,tempPerms.split(","));
        enode.save();
      }
      //Share activity
      try {
        ExoSocialActivity activity = null;
        if(node.getPrimaryNodeType().getName().equals(NodetypeConstant.NT_FILE)){
          activity = org.exoplatform.wcm.ext.component.activity.listener.Utils.postFileActivity(link,"",false,false,comment);
          activity.setType("sharefiles:spaces");
        }else{
          activity = org.exoplatform.wcm.ext.component.activity.listener.Utils.postActivity(link,"",false,false,comment);
          activity.setType("sharecontents:spaces");
        }
        activity.getTemplateParams().put("NodePath", link.getPath());
        //activity.getTemplateParams().remove("mimeType");
        activity.getTemplateParams().put("mimeType", getMimeType(node));
        activity.getTemplateParams().put("MESSAGE", comment);
        //activity.setTitle("shared a document");
        ActivityManager activityManager = (ActivityManager) PortalContainer.getInstance().getComponentInstanceOfType(ActivityManager.class);
        activityManager.updateActivity(activity);
        
      } catch (Exception e1) {
        LOG.error(e1.getMessage(), e1);
      }
      
    } catch (RepositoryException e) {
      LOG.error(e.getMessage(), e);
    }
    return false;
  }
  private String getMimeType(Node node) {
    try {
      if (node.getPrimaryNodeType().getName().equals(NodetypeConstant.NT_FILE)) {
        if (node.hasNode(NodetypeConstant.JCR_CONTENT))
          return node.getNode(NodetypeConstant.JCR_CONTENT)
                     .getProperty(NodetypeConstant.JCR_MIME_TYPE)
                     .getString();
      }
    } catch (RepositoryException e) {
      LOG.error(e.getMessage(), e);
    }
    return "";
  }
}
