/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.webui.components;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.dspace.app.util.ListUserCommunities;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.core.Context;
import org.dspace.eperson.Group;
import org.dspace.eperson. EPerson;
import org.dspace.plugin.PluginException;
import org.dspace.plugin.SiteHomeProcessor;


/**
 * This class add collections and subcomminities maps to the request attributes to use in
 * the site home  implementing the SiteHomeProcessor.
 *
 * @author Kate Pechekhonova
 *
 */
public class ListCommunitiesSiteProcessor implements SiteHomeProcessor
{

    /** log4j category */
    private static final Logger log = Logger.getLogger(ListCommunitiesSiteProcessor.class);

    /**
     * blank constructor - does nothing.
     *
     */
    public ListCommunitiesSiteProcessor()
    {

    }

    @Override
    public void process(Context context, HttpServletRequest request,
                        HttpServletResponse response) throws PluginException
    {

        Map colMap = new HashMap<Integer, Collection[]>();
        Map commMap = new HashMap<Integer, Community[]>();
        ArrayList<Collection> nyuOnly= ListUserCommunities.nyuOnly;

        if(ListUserCommunities.colMapAnon==null && ListUserCommunities.commMapAnon==null) {
            try {
                ListUserCommunities.ListAnonUserCommunities();
            } catch (SQLException e) {
                throw new PluginException(e.getMessage(), e);
            }
        }

        EPerson user = context.getCurrentUser();

        if(user==null)
        {
            log.error(" we do not have user ");
            // Get the top communities to shows in the community list
           if(ListUserCommunities.colMapAnon!=null) {
               colMap.putAll(ListUserCommunities.colMapAnon);
           }
           if(ListUserCommunities.commMapAnon!=null) {
                commMap.putAll(ListUserCommunities.commMapAnon);
           }

        }
        else {
            // Get the top communities to shows in the community list

            try {
                if(AuthorizeManager.isAdmin(context)) {
                  if(ListUserCommunities.colMapAdmin!=null) {
                      colMap.putAll(ListUserCommunities.colMapAdmin);
                  }
                  if(ListUserCommunities.commMapAdmin!=null) {
                      commMap.putAll(ListUserCommunities.commMapAdmin);
                  }
                } else {
                    int userID = user.getID();
                    if(ListUserCommunities.commAuthorizedUsers.containsKey(userID)|| ListUserCommunities.colAuthorizedUsers.containsKey(userID) ) {
                        ListCommunities comList = new ListCommunities();
                        comList.ListUserCommunities(context);
                        if(comList.getCollectionsMap()!=null) {
                            colMap.putAll(comList.getCollectionsMap());
                        }
                        if(comList.getCommunitiesMap()!=null) {
                            commMap.putAll(comList.getCommunitiesMap());
                        }
                    } else {
                        if(ListUserCommunities.colMapAnon!=null) {
                            colMap.putAll(ListUserCommunities.colMapAnon);
                        }
                        if(ListUserCommunities.commMapAnon!=null) {
                            commMap.putAll(ListUserCommunities.commMapAnon);
                        }
                    }

                }
            } catch (SQLException e) {
                throw new PluginException(e.getMessage(), e);
            }
            request.setAttribute("collections.map", colMap);
            request.setAttribute("subcommunities.map", commMap);
            request.setAttribute("nyuOnly", nyuOnly);

        }



    }

}
