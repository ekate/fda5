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
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
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

        /*if(context.getCurrentUser()==null)
        {
           /* // Get the top communities to shows in the community list
            Map colMapAnon = new HashMap<Integer, Collection[]>();
            Map commMapAnon = new HashMap<Integer, Community[]>();

            try {

                ListUserCommunities.ListAnonUserCommunities(context);
                colMapAnon = ListUserCommunities.colMapAnon;
                commMapAnon = ListUserCommunities.commMapAnon;

            } catch (SQLException e) {
                throw new PluginException(e.getMessage(), e);
            }
            request.setAttribute("collections.map", colMapAnon);
            request.setAttribute("subcommunities.map", commMapAnon);
        }
        else
        {*/
            // Get the top communities to shows in the community list
            Map colMap = new HashMap<Integer, Collection[]>();
            Map commMap = new HashMap<Integer, Community[]>();
            ArrayList<Collection> nyuOnly= new ArrayList<Collection>();

            //first time we generate the list then get it from cache
            try
            {
                ListUserCommunities comList = new  ListUserCommunities(context);
                colMap = comList.getCollectionsMap();
                commMap= comList.getCommunitiesMap();
                nyuOnly = comList.getNYUOnly();

            }
            catch (SQLException e)
            {
                throw new PluginException(e.getMessage(), e);
            }
            request.setAttribute("collections.map", colMap);
            request.setAttribute("subcommunities.map", commMap);
            request.setAttribute("nyuOnly", nyuOnly);
        //}
    }

}