/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 * <p>
 * http://www.dspace.org/license/
 */
package org.dspace.app.util;


import java.sql.SQLException;
import java.util.*;

import org.apache.log4j.Logger;

import org.dspace.content.Community;
import org.dspace.content.Collection;
import org.dspace.content.DSpaceObject;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import  org.dspace.eperson.EPerson;
import  org.dspace.eperson.Group;

/**
 * This class generates list of collections and c ommunities which will be listed on home page and on community pages
 * Anonymous users can only see - public, NYU only and school specific (Gallatin) collection which are not empty
 * Collection, community and site admins can see empty and private collections which ther administrate. Also submitters
 * can see empty collections to which they can add items.
 * We make all initial calculations when jspui applications starts and then do updates when changes are made, e.g.
 * when items are added to empty collection, when new admins are added to private collections or when collection permissions
 * are modified.
 *
 * @author Kate Pechekhonova
 *
 */
public class ListUserCommunities {


    // This contains list of NYU only collections
    public static ArrayList<Collection> nyuOnly;

    // This contains list of Gallatin only collectiuons
    public static ArrayList<Collection> gallatinOnly;

    // This contains list of empty collections
    public static ArrayList<Collection> emptyCollections;

    // This contains list of private collections
    public static ArrayList<Collection> privateCollections;

    // This will map collectionIDs to arrays of collections
    public static Map<Integer, Collection[]> colMapAnon;

    // This will map communityIDs to arrays of sub-communities
    public static Map<Integer, Community[]> commMapAnon;

    // This will map collectionIDs to arrays of collections
    public static Map<Integer, Collection[]> colMapAdmin;

    // This will map communityIDs to arrays of sub-communities
    public static Map<Integer, Community[]> commMapAdmin;

    // This will map collectionIDs to arrays of collections
    public static Map<Integer, Collection[]> colAuthorizedUsers;

    // This will map communityIDs to arrays of sub-communities
    public static Map<Integer, Community[]> commAuthorizedUsers;


    private static final Object staticLock = new Object();

    /**
     * Our context
     */
    protected Context ourContext;


    /**
     * log4j category
     */
    private static Logger log = Logger.getLogger(ListUserCommunities.class);



    public void ListUserCommunities() {

    }

    //generates maps for anon user, for site admins and other
    public static synchronized void ListAnonUserCommunities() throws java.sql.SQLException {

        Context context = new Context();

        if( colMapAnon==null && commMapAnon==null) {

            colMapAnon = new HashMap<Integer, Collection[]>();
            commMapAnon = new HashMap<Integer, Community[]>();
            colMapAdmin = new HashMap<Integer, Collection[]>();
            commMapAdmin = new HashMap<Integer, Community[]>();
            colAuthorizedUsers = new HashMap<Integer, Collection[]>();
            commAuthorizedUsers = new HashMap<Integer, Community[]>();
            nyuOnly = new ArrayList<Collection>();
            gallatinOnly = new ArrayList<Collection>();
            privateCollections = new ArrayList<Collection>();
            emptyCollections = new ArrayList<Collection>();


            Community[] communities = Community.findAll(context);

            if(communities!=null) {
                //user only can see non-empty, not private collections
                for (int com = 0; com < communities.length; com++) {
                    buildCollection(communities[com], context);
                }
            }

            Community[] communitiesAval = Community.findAllTop(context);

            if(communitiesAval!=null) {
                // we only include communities which has collections that the user can see
                for (int com = 0; com < communitiesAval.length; com++) {
                    buildCommunity(communitiesAval[com], context);
                }
            }
        }

    }

    public static synchronized void addCollectionToPrivateList(Collection col) throws java.sql.SQLException {


        if( privateCollections==null) {

            privateCollections = new ArrayList<Collection>();

        }
        privateCollections.add(col);
        getAuthirizedCollectionUsers(col);
    }

    public static synchronized void removeCollectionFromPrivateListID(int collectionID) throws java.sql.SQLException {

        if( privateCollections!=null) {

            for(Collection col:privateCollections) {
                if(col.getID()== collectionID) {
                    privateCollections.remove(col);
                }
            }

        }
    }

    public static synchronized void removeCollectionFromPrivateList(Collection col) throws java.sql.SQLException {

        if( privateCollections!=null && privateCollections.contains(col)) {

            privateCollections.remove(col);
            removeUsersFromAuthorizedColList(col);

        }
    }

    public static synchronized void addCollectionToNYUOnlyList(Collection col) throws java.sql.SQLException {

        if( nyuOnly==null) {

            emptyCollections = new ArrayList<Collection>();

        }
        nyuOnly.add(col);
    }

    public static synchronized void removeCollectionFromNYUOnlyListID(int collectionID) throws java.sql.SQLException {

        if( nyuOnly!=null) {

            for(Collection col:nyuOnly) {
                if(col.getID()== collectionID) {

                    nyuOnly.remove(col);
                }
            }

        }

    }

    public static synchronized void removeCollectionFromNYUOnlyList(Collection col) throws java.sql.SQLException {

        if( nyuOnly!=null && nyuOnly.contains(col)) {

            nyuOnly.remove(col);

        }

    }

    public static synchronized void addCollectionToGallatinOnlyList(Collection col) throws java.sql.SQLException {

        if( gallatinOnly==null) {

            gallatinOnly = new ArrayList<Collection>();

        }
        gallatinOnly.add(col);
    }

    public static synchronized void removeCollectionFromGallatinOnlyListID(int collectionID) throws java.sql.SQLException {

        if( gallatinOnly!=null) {

            for(Collection col:gallatinOnly) {
                if(col.getID()== collectionID) {
                    gallatinOnly.remove(col);
                }
            }
        }

    }

    public static synchronized void removeCollectionFromGallatinOnlyList(Collection col) throws java.sql.SQLException {

        if( gallatinOnly!=null && gallatinOnly.contains(col)) {

            gallatinOnly.remove(col);

        }

    }

    public static synchronized void addCollectionToEmptyList(Collection col) throws java.sql.SQLException {

        if( emptyCollections==null) {

            emptyCollections = new ArrayList<Collection>();

        }
        emptyCollections.add(col);
        getAuthirizedCollectionUsers(col);

    }

    public static synchronized void removeCollectionFromEmptyListID(int collectionID) throws java.sql.SQLException {

        if( emptyCollections!=null) {
            for(Collection col:emptyCollections) {
                if(col.getID()== collectionID) {
                    emptyCollections.remove(col);
                }
            }

        }

    }

    public static synchronized void removeCollectionFromEmptyList(Collection col) throws java.sql.SQLException {

        if( emptyCollections!=null && emptyCollections.contains(col)) {

            emptyCollections.remove(col);
            removeUsersFromAuthorizedColList(col);

        }

    }

    public static synchronized void addCollectionToAnnonList(Collection col) throws java.sql.SQLException {


        if( colMapAnon==null ) {
            colMapAnon = new HashMap<Integer, Collection[]>();
        }

        if( commMapAnon==null ) {
            commMapAnon = new HashMap<Integer, Community[]>();
        }

        addCollection(col, false);

    }

    public static synchronized void addCollectionToAnnonListID(Community comm, Collection col) throws java.sql.SQLException {


        if( colMapAnon==null ) {
            colMapAnon = new HashMap<Integer, Collection[]>();
        }

        if( commMapAnon==null ) {
            commMapAnon = new HashMap<Integer, Community[]>();
        }

        addCollectionID(comm, col, false);

    }

    public static synchronized void removeCollectionFromAnnonList(Collection col) throws java.sql.SQLException {

        if( colMapAnon!=null || commMapAnon!=null ) {
            removeCollection(col, false);
        }
    }

    public static synchronized void removeCollectionFromAnnonListID(Community comm, int collectionID) throws java.sql.SQLException {

        if( colMapAnon!=null || commMapAnon!=null ) {
            removeCollectionID(comm, collectionID,false);
        }
    }

    public static synchronized void addCollectionToAdminList(Collection col) throws java.sql.SQLException {

        if( colMapAdmin==null ) {
            colMapAdmin = new HashMap<Integer, Collection[]>();
        }

        if( commMapAdmin==null ) {
            commMapAdmin = new HashMap<Integer, Community[]>();
        }
        log.warn("Start modifying admin list");
        addCollection(col, true);

    }

    public static synchronized void addCollectionToAdminListID(Community com, Collection col) throws java.sql.SQLException {

        if( colMapAdmin==null ) {
            colMapAdmin = new HashMap<Integer, Collection[]>();
        }

        if( commMapAdmin==null ) {
            commMapAdmin = new HashMap<Integer, Community[]>();
        }
        log.warn("Start modifying admin list");
        addCollectionID(com, col, true);

    }


    public static synchronized void removeCollectionFromAdminList(Collection col) throws java.sql.SQLException {

        if( colMapAdmin!=null || commMapAdmin!=null ) {
            removeCollection(col, true);
        }

    }

    public static synchronized void removeCollectionFromAdminListID(Community com, int collectionID) throws java.sql.SQLException {

        if( colMapAdmin!=null || commMapAdmin!=null ) {
            removeCollectionID(com, collectionID, true);
        }

    }




    public static synchronized void addCommunityToAnnonList(Community com) throws java.sql.SQLException {

        if( commMapAdmin!=null ) {
            addParentComm(com, false);
            addChildrenComm(com, false);
        }
    }

    public static synchronized void removeCommunityFromAnnonListID(Community parentComm, int communityID) throws java.sql.SQLException {

        if(  commMapAdmin!=null ) {
            removeParentCommID(parentComm, communityID, false);
        }


    }
    public static synchronized void removeCommunityFromAnnonList(Community com) throws java.sql.SQLException {

        if(  commMapAdmin!=null ) {
            removeParentComm(com, false);
            removeChildrenComm(com, false);
        }


    }

    public static synchronized void addCommunityToAdminList(Community com) throws java.sql.SQLException {

            addParentComm(com, true);
            addChildrenComm(com, true);
        log.warn(" updated community "+ commMapAdmin.get(com.getParentCommunity().getID()).length);

    }

    public static synchronized void addCommunityToAdminListID(int communityID) throws java.sql.SQLException {

        if( commMapAdmin==null ) {
            commMapAdmin = new HashMap<Integer, Community[]>();
        }
        commMapAdmin.put(communityID,null);

    }

    public static synchronized void removeCommunityFromAdminListID(Community com, int communityID) throws java.sql.SQLException {

        if(  commMapAdmin!=null ) {
            removeParentCommID(com,communityID, true);
        }

    }

    public static synchronized void removeCommunityFromAdminList(Community com) throws java.sql.SQLException {

        if(  commMapAdmin!=null ) {
            removeParentComm(com, true);
            removeChildrenComm(com, true);
        }

    }

    public static synchronized void removeChildrenCommunityFromAnnonListID(int communityID) throws java.sql.SQLException {

        if(  commMapAnon!=null ) {
            removeChildrenCommID(communityID, false);

        }

    }

    public static synchronized void removeChildrenCommunityFromAdminListID(int communityID) throws java.sql.SQLException {

        if(  commMapAdmin!=null ) {
            removeChildrenCommID(communityID, true);
        }

    }

    public static  void addUsersToAuthorizedColList(Collection col) throws java.sql.SQLException {

        ArrayList<EPerson> epersons = getAuthirizedCollectionUsers(col);
        log.error("admins: " + epersons.size());
        for (EPerson eperson : epersons) {
            log.error("eperson: " + eperson.getName());
            if (colAuthorizedUsers.containsKey(eperson.getID())) {
                Collection[] colsOld = colAuthorizedUsers.get(eperson.getID());
                if (colsOld != null) {
                    LinkedList<Collection> colsOldRaw = new LinkedList(Arrays.asList(colsOld));
                    if (!colsOldRaw.contains(col)) {
                        colsOldRaw.add(col);
                        Collection[] colsNew = new Collection[colsOldRaw.size()];
                        colAuthorizedUsers.put(eperson.getID(), colsOldRaw.toArray(colsNew));
                        log.error("added to map: " + colAuthorizedUsers.get(eperson.getID()).length);
                    }
                }
            } else {
                Collection[] colsNew = {col};
                colAuthorizedUsers.put(eperson.getID(), colsNew);
            }
        }

    }

    public static  void removeUsersFromAuthorizedColList(Collection col) throws java.sql.SQLException {

        ArrayList<EPerson> epersons = getAuthirizedCollectionUsers(col);

        for (EPerson eperson : epersons) {
            log.error("eperson: " + eperson.getName());
            if (colAuthorizedUsers.containsKey(eperson.getID())) {
                Collection[] colsOld = colAuthorizedUsers.get(eperson.getID());
                if (colsOld != null) {
                    LinkedList<Collection> colsOldRaw = new LinkedList(Arrays.asList(colsOld));
                    if (colsOldRaw.contains(col)) {
                        colsOldRaw.remove(col);
                        Collection[] colsNew = new Collection[colsOldRaw.size()];
                        colAuthorizedUsers.put(eperson.getID(), colsOldRaw.toArray(colsNew));
                        log.error("added to map: " + colAuthorizedUsers.get(eperson.getID()).length);
                    }
                }
            }
        }

    }

    public static synchronized void addAuthorizedUser(DSpaceObject dso, EPerson eperson) {
        int epersonID = eperson.getID();
        if(dso.getType()== Constants.COMMUNITY) {
            if(commAuthorizedUsers==null) {
                commAuthorizedUsers = new HashMap<Integer, Community[]>();
            }
            Community com = (Community) dso;
            if(commAuthorizedUsers.containsKey(epersonID)) {
                Community[] commsOld = commAuthorizedUsers.get(epersonID);
                if (commsOld != null) {
                    LinkedList<Community> commsOldRaw = new LinkedList(Arrays.asList(commsOld));
                    if (!commsOldRaw.contains(com)) {
                        commsOldRaw.add(com);
                        Community[] commsNew = new Community[commsOldRaw.size()];
                        commAuthorizedUsers.put(epersonID, commsOldRaw.toArray(commsNew));
                        log.error("added to map: " + colAuthorizedUsers.get(eperson.getID()).length);
                    }
                }

            } else {
                    Community[] comms = {com};
                    commAuthorizedUsers.put(epersonID, comms );
            }

        }

        if(dso.getType()== Constants.COLLECTION) {
            if(colAuthorizedUsers==null) {
                colAuthorizedUsers = new HashMap<Integer, Collection[]>();
            }
            Collection col = (Collection) dso;
            if(colAuthorizedUsers.containsKey(epersonID)) {
                Collection[] colsOld = colAuthorizedUsers.get(epersonID);
                if (colsOld != null) {
                    List<Collection> colsOldRaw = (List<Collection>) Arrays.asList(colsOld);
                    if (!colsOldRaw.contains(col)) {
                        colsOldRaw.add(col);
                        Collection[] colsNew = new Collection[colsOldRaw.size()];
                        colAuthorizedUsers.put(epersonID, colsOldRaw.toArray(colsNew));
                        log.error("added to map: " + colAuthorizedUsers.get(eperson.getID()).length);
                    }
                }

            } else {
                Collection[] cols = {col};
                colAuthorizedUsers.put(epersonID, cols );
            }

        }

    }

    public static synchronized void removeAuthorizedUser(DSpaceObject dso, EPerson eperson) {
        int epersonID = eperson.getID();
        if(dso.getType()== Constants.COMMUNITY) {
            if(commAuthorizedUsers!=null) {
                Community com = (Community) dso;

                if (commAuthorizedUsers.containsKey(epersonID)) {
                    Community[] commsOld = commAuthorizedUsers.get(epersonID);
                    if (commsOld != null) {
                        LinkedList<Collection> commsOldRaw = new LinkedList(Arrays.asList(commsOld));
                        if (commsOldRaw.contains(com)) {
                            commsOldRaw.remove(com);
                            if (commsOldRaw.size() > 0) {
                                Community[] commsNew = new Community[commsOldRaw.size()];
                                commAuthorizedUsers.put(epersonID, commsOldRaw.toArray(commsNew));
                            } else {
                                commAuthorizedUsers.remove(epersonID);
                            }

                        }
                    }

                }
            }

        }

        if(dso.getType()== Constants.COLLECTION) {
            if (colAuthorizedUsers != null) {
                Collection col = (Collection) dso;

                if (colAuthorizedUsers.containsKey(epersonID)) {
                    Collection[] colsOld = colAuthorizedUsers.get(epersonID);
                    if (colsOld != null) {
                        List<Collection> colsOldRaw = (List<Collection>) Arrays.asList(colsOld);
                        if (colsOldRaw.contains(col)) {
                            colsOldRaw.remove(col);
                            if (colsOldRaw.size() > 0) {
                                Collection[] colsNew = new Collection[colsOldRaw.size()];
                                colAuthorizedUsers.put(epersonID, colsOldRaw.toArray(colsNew));
                            } else {
                                colAuthorizedUsers.remove(epersonID);
                            }

                        }
                    }

                }
            }
        }
    }

    public static  void addUsersToAuthorizedComList(Community com) throws java.sql.SQLException {
        ArrayList<EPerson> epersons = getAuthirizedCommunityUsers(com);
        log.error("admins: " + epersons.size());
        for (EPerson eperson : epersons) {
            log.error("eperson: " + eperson.getName());
            if (commAuthorizedUsers.containsKey(eperson.getID())) {
                Community[] commsOld = commAuthorizedUsers.get(eperson.getID());
                if (commsOld != null) {
                    LinkedList<Community> commsOldRaw = new LinkedList(Arrays.asList(commsOld));
                    if (!commsOldRaw.contains(com)) {
                        commsOldRaw.add(com);
                        Community[] commsNew = new Community[commsOldRaw.size()];
                        commAuthorizedUsers.put(eperson.getID(), commsOldRaw.toArray(commsNew));
                        log.error("added to map: " + colAuthorizedUsers.get(eperson.getID()).length);
                    }
                }
            } else {
                Community[] commsNew = {com};
                commAuthorizedUsers.put(eperson.getID(), commsNew);
            }
        }


    }

    public static  void removeUsersFromAuthorizedComList(Community com) throws java.sql.SQLException {

        ArrayList<EPerson> epersons = getAuthirizedCommunityUsers(com);

        for (EPerson eperson : epersons) {
            log.error("eperson: " + eperson.getName());
            if (colAuthorizedUsers.containsKey(eperson.getID())) {
                Community[] commsOld = commAuthorizedUsers.get(eperson.getID());
                if (commsOld != null) {
                    LinkedList<Community> commsOldRaw = new LinkedList(Arrays.asList(commsOld));
                    if (commsOldRaw.contains(com)) {
                        commsOldRaw.remove(com);
                        Community[] colsNew = new Community[commsOldRaw.size()];
                        commAuthorizedUsers.put(eperson.getID(), commsOldRaw.toArray(colsNew));
                        log.error("added to map: " + colAuthorizedUsers.get(eperson.getID()).length);
                    }
                }
            }
        }

    }






    private static void buildCollection(Community c, Context context) throws SQLException {
        Integer comID = c.getID();

        Collection[] colls = c.getCollections();

        colMapAdmin.put(comID, colls);
        log.error("community: "+c.getName()+" size: "+ colMapAdmin.get(comID).length);

        if (colls.length > 0) {
            log.error("length"+colls.length);
            ArrayList<Collection> availableCol = new ArrayList<Collection>();


            for (int i = 0; i < colls.length; i++) {

                int countItems = colls[i].countItems();
                if (colls[i].isPublic()) {
                    if(countItems>0) {
                        availableCol.add(colls[i]);
                    } else {
                        emptyCollections.add(colls[i]);
                    }
                } else {
                    if (colls[i].isNYUOnly()) {
                        if(countItems>0) {
                            availableCol.add(colls[i]);
                        } else {
                            emptyCollections.add(colls[i]);
                        }
                        nyuOnly.add(colls[i]);

                    } else {

                        if (colls[i].isGallatin()) {
                            if(countItems>0) {
                                availableCol.add(colls[i]);
                            } else {
                                emptyCollections.add(colls[i]);
                            }
                            gallatinOnly.add(colls[i]);
                        }
                    }
                }

                if (!availableCol.contains(colls[i])) {

                    addUsersToAuthorizedColList(colls[i]);


                    if(colls[i].isPrivate()) {
                        privateCollections.add(colls[i]);
                    }
                     if(countItems==0) {
                        emptyCollections.add(colls[i]);
                     }

                }
            }
            if (availableCol.size() > 0) {
                Collection[] availableColArray = new Collection[availableCol.size()];
                colMapAnon.put(comID, availableCol.toArray(availableColArray));
            }
        }

    }


    private static void buildCommunity(Community c, Context context) throws SQLException {
        Integer comID = c.getID();

        Community[] comms = c.getSubcommunities();

        commMapAdmin.put(comID, comms);

        if (comms.length > 0) {
            ArrayList<Community> availableComm = new ArrayList<Community>();

            for (int i = 0; i < comms.length; i++) {

                buildCommunity(comms[i], context);

                if (colMapAnon.containsKey(comms[i].getID())
                        || commMapAnon.containsKey(comms[i].getID())) {
                    availableComm.add(comms[i]);
                } else {
                    ArrayList<EPerson> epersons = getAuthirizedCommunityUsers(comms[i]);
                    for(EPerson eperson:epersons) {
                        log.warn("eperson:"+eperson.getName());
                        if(commAuthorizedUsers.containsKey(eperson.getID())) {
                            Community[] commsOld = commAuthorizedUsers.get(eperson.getID());
                            if(commsOld!=null) {
                                List<Community> commsOldRaw = (List<Community>) Arrays.asList(commsOld);
                                if (!commsOldRaw.contains(comms[i])) {
                                    ArrayList<Community> commsNewRaw = new ArrayList<Community>();
                                    commsNewRaw.add(comms[i]);
                                    for (Object commsold : commsOldRaw) {
                                        commsNewRaw.add((Community) commsold);
                                    }

                                    Community[] commsNew = new Community[commsNewRaw.size()];
                                    commAuthorizedUsers.put(eperson.getID(), commsNewRaw.toArray(commsNew));
                                }
                            }
                        } else {
                            Community[] commsNew = {comms[i]};
                            commAuthorizedUsers.put(eperson.getID(), commsNew );
                        }
                    }
                }
            }
            if (availableComm.size() > 0) {
                Community[] availableCommArray = new Community[availableComm.size()];
                commMapAnon.put(comID, availableComm.toArray(availableCommArray));
            }
        }
    }



    private static ArrayList<EPerson> getAuthirizedCollectionUsers(Collection col) throws SQLException {

            ArrayList<EPerson> epersons= new ArrayList<EPerson>();
            ArrayList<EPerson> allusers= new ArrayList<EPerson>();

            Group admins = col.getAdministrators();
            if(admins!=null) {
              allusers =  getAllGroupUsers(admins);
                if(allusers!=null) {
                    epersons.addAll(allusers);
                }
            }

        Group submitters = col.getSubmitters();
        if(submitters!=null) {
            allusers =  getAllGroupUsers(submitters);
            if(allusers!=null) {
                epersons.addAll(allusers);
            }
        }

        Community[] parentComms = col.getCommunities();
        for(Community  parentComm:parentComms) {
            allusers = getAuthirizedGroup(parentComm);
            if( allusers!=null) {
                epersons.addAll(allusers);
            }
        }

        return epersons;
    }

    private static ArrayList<EPerson> getAuthirizedCommunityUsers(Community com) throws SQLException {

        ArrayList<EPerson> epersons= new ArrayList<EPerson>();

        epersons = getAuthirizedGroup(com);
        Community[] parentComms = com.getAllParents();
        for(Community  parentComm:parentComms) {
            ArrayList<EPerson> parentEpersons = getAuthirizedGroup(parentComm);
            if( parentEpersons!=null) {
                epersons.addAll(parentEpersons);
            }
        }
        return epersons;
    }

    private static ArrayList<EPerson> getAuthirizedGroup(Community com) throws SQLException {
        ArrayList<EPerson> epersons= new ArrayList<EPerson>();
        ArrayList<EPerson> allusers= new ArrayList<EPerson>();

        Group admins = com.getAdministrators();
        if(admins!=null) {
            allusers =  getAllGroupUsers(admins);
            if(allusers!=null) {
                epersons.addAll(allusers);
            }
        }
        return epersons;
    }

    private static ArrayList<EPerson> getAllGroupUsers(Group g) {

        ArrayList<EPerson> epersons= new ArrayList<EPerson>();

        for(EPerson eperson:g.getMembers()) {
           epersons.add(eperson);
        }
        for(Group ag:g.getMemberGroups()) {
            for(EPerson geperson:ag.getMembers()) {
                epersons.add(geperson);
            }

        }
        return epersons;
    }

    private static void addChildrenComm( Community com, Boolean admin ) throws java.sql.SQLException {
        int comID = com.getID();
        Collection[] colls = com.getCollections();
        if (colls.length > 0) {
            if(admin) {
                colMapAdmin.put(comID, colls);
            } else {
                colMapAnon.put(comID, colls);
            }
        }
        Community[] comms = com.getSubcommunities();
            if(admin) {
                if(commMapAdmin == null) {
                    commMapAdmin.put(comID, comms);
                } else {
                    if(!commMapAdmin.containsKey(comID)) {
                        commMapAdmin.put(comID, comms);
                    }
                }
            } else {
                if (comms.length > 0 ) {
                    if(commMapAnon == null) {
                        commMapAnon.put(comID, comms);
                    } else {
                        if (!commMapAnon.containsKey(comID)) {
                            commMapAnon.put(comID, comms);
                        }
                    }
                }
            }


            for(Community subcomm:comms) {
                addChildrenComm(  subcomm, admin);
            }
        }

    private static void addCollection(Collection col, Boolean admin ) throws java.sql.SQLException {

        Community[] parentComms =  col.getCommunities();
        if(parentComms!=null) {
            log.warn(" get collection " + parentComms.length);

            for (Community parentComm : parentComms) {
                Collection[] colsChildren = parentComm.getCollections();
                if(colsChildren != null) {
                    LinkedList<Collection> colsChildrenRaw = new LinkedList(Arrays.asList(colsChildren));
                    if(colsChildrenRaw.contains(col)) {
                        int parentCommID = parentComm.getID();
                        if (admin) {
                            if (colMapAdmin.containsKey(parentCommID) && colMapAdmin.get(parentCommID) != null) {
                                Collection[] colsOld = colMapAdmin.get(parentCommID);

                                LinkedList<Collection> colsOldRaw = new LinkedList(Arrays.asList(colsOld));
                                if (!colsOldRaw.contains(col)) {
                                    colsOldRaw.add(col);
                                    Collection[] colsNew = new Collection[colsOldRaw.size()];
                                    colMapAdmin.put(parentCommID, colsOldRaw.toArray(colsNew));
                                }

                            } else {
                                Collection[] colsNew = {col};
                                colMapAdmin.put(parentComm.getID(), colsNew);
                            }
                        } else {
                            if (colMapAnon.containsKey(parentCommID) && colMapAnon.get(parentCommID) != null) {
                                Collection[] colsOld = colMapAnon.get(parentCommID);

                                LinkedList<Collection> colsOldRaw = new LinkedList(Arrays.asList(colsOld));
                                if (!colsOldRaw.contains(col)) {
                                    colsOldRaw.add(col);
                                    Collection[] colsNew = new Collection[colsOldRaw.size()];
                                    colMapAnon.put(parentCommID, colsOldRaw.toArray(colsNew));
                                }

                            } else {
                                Collection[] colsNew = {col};
                                colMapAnon.put(parentCommID, colsNew);
                            }
                        }
                        Community nextParentComm = parentComm.getParentCommunity();
                        if (nextParentComm != null) {
                            addParentComm(parentComm, admin);
                        }
                    }
                }
            }
        }
    }

    private static void addCollectionID(Community comm, Collection col, Boolean admin ) throws java.sql.SQLException {

                int parentCommID=comm.getID();
                log.warn("parent community id "+parentCommID);
                if (admin) {
                    if (colMapAdmin.containsKey(parentCommID) && colMapAdmin.get(parentCommID) != null) {
                        Collection[] colsOld = colMapAdmin.get(parentCommID);
                        LinkedList<Collection> colsOldRaw = new LinkedList(Arrays.asList(colsOld));
                        log.warn("collection array size "+colsOldRaw.size());
                        if (!colsOldRaw.contains(col)) {
                            log.warn("adding collection "+col.getName());
                            colsOldRaw.add(col);
                            log.warn("collection array new size "+colsOldRaw.size());
                            Collection[] colsNew = new Collection[colsOldRaw.size()];
                            log.warn("collection array  length "+colsNew.length);
                            colMapAdmin.put(parentCommID, colsOldRaw.toArray(colsNew));
                            log.warn(" new "+ colMapAdmin.get(parentCommID)[4].getName()+" no ");
                        }

                    } else {
                        Collection[] colsNew = {col};
                        colMapAdmin.put(parentCommID, colsNew);
                    }
                } else {
                    if (colMapAnon.containsKey(parentCommID) && colMapAnon.get(parentCommID) != null) {
                        Collection[] colsOld = colMapAnon.get(parentCommID);

                        LinkedList<Collection> colsOldRaw = new LinkedList(Arrays.asList(colsOld));
                        if (!colsOldRaw.contains(col)) {
                            colsOldRaw.add(col);
                            Collection[] colsNew = new Collection[colsOldRaw.size()];
                            colMapAnon.put(parentCommID, colsOldRaw.toArray(colsNew));
                        }

                    } else {
                        Collection[] colsNew = {col};
                        colMapAnon.put(parentCommID, colsNew);
                    }
                }
                Community nextParentComm = comm.getParentCommunity();
                if (nextParentComm != null) {
                    addParentComm(comm, admin);
                }

    }

    public static synchronized void updateCollectionMetadata( Collection col, Boolean admin ) throws java.sql.SQLException {

        if(col.getParentObject()!=null) {
            int parentCommID = col.getParentObject().getID();
            log.warn("parent community id " + parentCommID);
            if (admin) {
                if (colMapAdmin.containsKey(parentCommID) && colMapAdmin.get(parentCommID) != null) {
                    Collection[] colsOld = colMapAdmin.get(parentCommID);
                    LinkedList<Collection> colsOldRaw = new LinkedList(Arrays.asList(colsOld));
                    log.warn("collection array size " + colsOldRaw.size());
                    if (colsOldRaw.contains(col)) {
                        colsOldRaw.remove(col);
                        colsOldRaw.add(col);
                        log.warn("collection array new size " + colsOldRaw.size());
                        Collection[] colsNew = new Collection[colsOldRaw.size()];
                        log.warn("collection name "+col.getName()+ " added");
                        colMapAdmin.put(parentCommID, colsOldRaw.toArray(colsNew));
                    }

                }
            } else {
                if (colMapAnon.containsKey(parentCommID) && colMapAnon.get(parentCommID) != null) {
                    Collection[] colsOld = colMapAnon.get(parentCommID);

                    LinkedList<Collection> colsOldRaw = new LinkedList(Arrays.asList(colsOld));
                    if (colsOldRaw.contains(col)) {
                        colsOldRaw.remove(col);
                        colsOldRaw.add(col);
                        Collection[] colsNew = new Collection[colsOldRaw.size()];
                        colMapAnon.put(parentCommID, colsOldRaw.toArray(colsNew));
                    }

                }
            }

        }

    }



    private static void addParentComm( Community com, Boolean admin ) throws java.sql.SQLException {
        Community parentComm = (Community) com.getParentCommunity();
        log.warn(" community "+com.getName()+ " no");
        if(parentComm!=null) {
            log.warn(" parent community "+parentComm.getName());
            if(admin) {
                if (commMapAdmin.containsKey(parentComm.getID()) && commMapAdmin.get(parentComm.getID()) != null) {
                    Community[] commsOld = commMapAdmin.get(parentComm.getID());
                    LinkedList<Community> commsOldRaw = new LinkedList(Arrays.asList(commsOld));
                    if (!commsOldRaw.contains(com)) {
                        commsOldRaw.add(com);
                        Community[] commNew = new Community[commsOldRaw.size()];
                        commMapAdmin.put(parentComm.getID(), commsOldRaw.toArray(commNew));
                    }
                } else {
                    Community[] commNew = {com};
                    commMapAdmin.put(parentComm.getID(), commNew);
                }
            } else {
                if (commMapAnon.containsKey(parentComm.getID()) && commMapAnon.get(parentComm.getID()) != null) {
                    Community[] commsOld = commMapAnon.get(parentComm.getID());
                    LinkedList<Community> commsOldRaw = new LinkedList(Arrays.asList(commsOld));
                    if (!commsOldRaw.contains(com)) {

                        commsOldRaw.add(com);

                        Community[] commNew = new Community[commsOldRaw.size()];
                        commMapAnon.put(parentComm.getID(), commsOldRaw.toArray(commNew));
                    }
                } else {
                    Community[] commNew = {com};
                    commMapAnon.put(parentComm.getID(), commNew);
                }
            }
            Community nextParentComm = parentComm.getParentCommunity();
            if (nextParentComm != null) {
                log.warn(" add to community "+nextParentComm.getName());
                addParentComm(parentComm, admin);
            }
        }
    }

    public static synchronized void updateCommunityMetadata( Community com, Boolean admin ) throws java.sql.SQLException {
        Community parentComm = (Community) com.getParentCommunity();
        if(parentComm!=null) {
            if (admin) {
                if (commMapAdmin.containsKey(parentComm.getID()) && commMapAdmin.get(parentComm.getID()) != null) {
                    Community[] commsOld = commMapAdmin.get(parentComm.getID());
                    LinkedList<Community> commsOldRaw = new LinkedList(Arrays.asList(commsOld));
                    if (commsOldRaw.contains(com)) {
                        commsOldRaw.remove(com);
                        commsOldRaw.add(com);
                        Community[] commNew = new Community[commsOldRaw.size()];
                        commMapAdmin.put(parentComm.getID(), commsOldRaw.toArray(commNew));
                    }
                } else {
                    Community[] commNew = {com};
                    commMapAdmin.put(parentComm.getID(), commNew);
                }
            } else {
                if (commMapAnon.containsKey(parentComm.getID()) && commMapAnon.get(parentComm.getID()) != null) {
                    Community[] commsOld = commMapAnon.get(parentComm.getID());
                    LinkedList<Community> commsOldRaw = new LinkedList(Arrays.asList(commsOld));
                    if (commsOldRaw.contains(com)) {
                        commsOldRaw.remove(com);
                        commsOldRaw.add(com);
                        Community[] commNew = new Community[commsOldRaw.size()];
                        commMapAnon.put(parentComm.getID(), commsOldRaw.toArray(commNew));
                    }
                }
            }
        }
    }

    private static void removeCollection(Collection col, Boolean admin ) throws java.sql.SQLException {

        Community[] parentComms =  col.getCommunities();
        for (Community parentComm:parentComms) {
            //need to check that it is "primary" parent collection
            Collection[] colsAll = parentComm.getCollections();
            List<Collection> colsAllRaw =  Arrays.asList(colsAll);
            if (colsAllRaw.contains(col)) {
                if(admin) {
                    if (colMapAdmin.containsKey(parentComm.getID()) && colMapAdmin.get(parentComm.getID()) != null) {
                        Collection[] colsOld = colMapAdmin.get(parentComm.getID());

                        LinkedList<Collection> colsOldRaw = new LinkedList(Arrays.asList(colsOld));

                        if (colsOldRaw.contains(col)) {
                            colsOldRaw.remove(col);
                            if(colsOldRaw.size()>0) {
                                Collection[] colsNew = new Collection[colsOldRaw.size()];
                                colMapAdmin.put(parentComm.getID(), colsOldRaw.toArray(colsNew));
                            } else {
                                colMapAdmin.remove(parentComm.getID());
                                if(!commMapAdmin.containsKey(parentComm.getID())) {
                                    Community nextParentComm = parentComm.getParentCommunity();
                                    if (nextParentComm != null) {
                                        removeParentComm(parentComm, admin);
                                    }
                                }
                            }
                        }

                    }

                } else {
                    if (colMapAnon.containsKey(parentComm.getID()) && colMapAnon.get(parentComm.getID()) != null) {
                        Collection[] colsOld = colMapAnon.get(parentComm.getID());

                        LinkedList<Collection> colsOldRaw = new LinkedList(Arrays.asList(colsOld));
                        if (colsOldRaw.contains(col)) {
                            colsOldRaw.remove(col);
                            if(colsOldRaw.size()>0) {
                            Collection[] colsNew = new Collection[colsOldRaw.size()];
                            colMapAnon.put(parentComm.getID(), colsOldRaw.toArray(colsNew));
                            } else {
                                colMapAnon.remove(parentComm.getID());
                                if(!commMapAnon.containsKey(parentComm.getID())) {
                                    Community nextParentComm = parentComm.getParentCommunity();
                                    if (nextParentComm != null) {
                                        removeParentComm(parentComm, admin);
                                    }
                                }
                            }
                        }

                    }
                }

            }
        }

    }

    private static void removeCollectionID(Community comm, int collectionID, Boolean admin ) throws java.sql.SQLException {
                int communityID = comm.getID();
                if(admin) {
                    if (colMapAdmin.containsKey(communityID) && colMapAdmin.get(communityID) != null) {
                        Collection[] colsOld = colMapAdmin.get(communityID);

                        LinkedList<Collection> colsOldRaw = new LinkedList(Arrays.asList(colsOld));

                        for (Collection col:colsOldRaw) {
                            if(col.getID()==collectionID) {
                                colsOldRaw.remove(col);
                                if (colsOldRaw.size() > 0) {
                                    Collection[] colsNew = new Collection[colsOldRaw.size()];
                                    colMapAdmin.put(communityID, colsOldRaw.toArray(colsNew));
                                } else {
                                    colMapAdmin.remove(communityID);
                                    if(!commMapAdmin.containsKey(communityID)) {
                                        Community nextParentComm = comm.getParentCommunity();
                                        if (nextParentComm != null) {
                                            removeParentComm(comm, admin);
                                        }
                                    }

                                }
                            }
                        }

                    }

                } else {
                    if (colMapAnon.containsKey(communityID) && colMapAnon.get(communityID) != null) {
                        Collection[] colsOld = colMapAnon.get(communityID);

                        LinkedList<Collection> colsOldRaw = new LinkedList(Arrays.asList(colsOld));

                        for (Collection col:colsOldRaw) {
                            if (col.getID() == collectionID) {
                                colsOldRaw.remove(col);
                                if (colsOldRaw.size() > 0) {
                                    Collection[] colsNew = new Collection[colsOldRaw.size()];
                                    colMapAnon.put(communityID, colsOldRaw.toArray(colsNew));
                                } else {
                                    colMapAnon.remove(communityID);
                                    if(!commMapAnon.containsKey(communityID)) {
                                        Community nextParentComm = comm.getParentCommunity();
                                        if (nextParentComm != null) {
                                            removeParentComm( comm, admin);
                                        }
                                    }
                                }
                            }
                        }

                    }
                }
    }

    private static void removeParentComm( Community com, Boolean admin ) throws java.sql.SQLException {
        Community parentComm = (Community) com.getParentCommunity();
        if(parentComm!=null) {
            if(admin) {
                if (commMapAdmin.containsKey(parentComm.getID()) && commMapAdmin.get(parentComm.getID()) != null) {
                    Community[] commsOld = commMapAdmin.get(parentComm.getID());
                    LinkedList<Community> commsOldRaw = new LinkedList(Arrays.asList(commsOld));
                    if (commsOldRaw.contains(com)) {
                        commsOldRaw.remove(com);
                        if (commsOldRaw.size() > 0) {
                            Community[] commNew = new Community[commsOldRaw.size()];
                            commMapAdmin.put(parentComm.getID(), commsOldRaw.toArray(commNew));
                        } else {
                            commMapAdmin.remove(parentComm.getID());
                        }
                    }
                }
            } else {
                if (commMapAnon.containsKey(parentComm.getID()) && commMapAnon.get(parentComm.getID()) != null) {
                    Community[] commsOld = commMapAnon.get(parentComm.getID());
                    LinkedList<Community> commsOldRaw = new LinkedList(Arrays.asList(commsOld));
                    if (commsOldRaw.contains(com)) {
                            commsOldRaw.remove(com);
                            if (commsOldRaw.size() > 0) {
                                Community[] commNew = new Community[commsOldRaw.size()];
                            commMapAnon.put(parentComm.getID(), commsOldRaw.toArray(commNew));
                        } else {
                            commMapAnon.remove(parentComm.getID());
                        }
                    }
                }
            }

            Community nextParentComm = parentComm.getParentCommunity();
            if (nextParentComm != null) {
                removeParentComm(nextParentComm, admin);
            }
        }
    }

    private static void removeChildrenComm( Community com, Boolean admin ) throws java.sql.SQLException {

        Collection[] colls = com.getCollections();
        if (colls.length > 0) {
            if(admin) {
                colMapAdmin.remove(com.getID());
            } else {
                colMapAnon.remove(com.getID());
            }
        }
        Community[] comms = com.getSubcommunities();
        if (comms.length > 0) {
            if(admin) {
                commMapAdmin.remove(com.getID());
            } else {
                commMapAnon.remove(com.getID());
            }
            for(Community subcomm:comms) {
                removeChildrenComm(  subcomm, admin);
            }
        }
    }

    private static void removeParentCommID(Community parentComm, int communityID, Boolean admin ) throws java.sql.SQLException {
        int parentCommID = parentComm.getID();
        if(admin) {
            if (commMapAdmin.containsKey(parentCommID) && commMapAdmin.get(parentCommID) != null) {
                Community[] commsOld = commMapAdmin.get(parentCommID);
                LinkedList<Community> commsOldRaw = new LinkedList(Arrays.asList(commsOld));
                for (Community comm: commsOldRaw) {
                    if(comm.getID()==communityID) {
                        commsOldRaw.remove(comm);
                        if (commsOldRaw.size() > 0) {
                            Community[] commNew = new Community[commsOldRaw.size()];
                            commMapAdmin.put(parentComm.getID(), commsOldRaw.toArray(commNew));
                        } else {
                            commMapAdmin.remove(parentComm.getID());
                        }
                    }
                }
            }
        } else {
            if (commMapAnon.containsKey(parentCommID) && commMapAnon.get(parentCommID) != null) {
                Community[] commsOld = commMapAnon.get(parentCommID);
                LinkedList<Community> commsOldRaw = new LinkedList(Arrays.asList(commsOld));
                for (Community comm: commsOldRaw) {
                    if(comm.getID()==communityID) {
                        commsOldRaw.remove(comm);
                        if (commsOldRaw.size() > 0) {
                            Community[] commNew = new Community[commsOldRaw.size()];
                            commMapAnon.put(parentComm.getID(), commsOldRaw.toArray(commNew));
                        } else {
                            commMapAnon.remove(parentComm.getID());
                        }
                    }
                }
            }
        }
    }

    private static void removeChildrenCommID(int communityID, Boolean admin) throws java.sql.SQLException {
        if(admin) {
            if (commMapAdmin!=null && commMapAdmin.containsKey(communityID)) {
                            commMapAdmin.remove(communityID);
            }
            if (colMapAdmin!=null && colMapAdmin.containsKey(communityID)) {
                colMapAdmin.remove(communityID);
            }
        } else {
            if (commMapAnon!=null && commMapAnon.containsKey(communityID) ) {
                commMapAnon.remove(communityID);
            }
            if (colMapAnon!=null && colMapAdmin.containsKey(communityID)) {
                colMapAdmin.remove(communityID);
            }

        }

    }

    public static void checkCollection(Collection collection) throws java.sql.SQLException {
        if(collection.isPrivate()) {
            if(privateCollections==null || !privateCollections.contains(collection)) {
                addCollectionToPrivateList(collection);
            }
        } else {
            if(privateCollections.contains(collection)) {
                removeCollectionFromPrivateList(collection);
            }
        }
        if(collection.isNYUOnly()) {
            if(nyuOnly==null || !nyuOnly.contains(collection)) {
                addCollectionToNYUOnlyList(collection);
            }
        } else {
            if(nyuOnly.contains(collection)) {
                removeCollectionFromNYUOnlyList(collection);
            }
        }
        if(collection.isGallatin()) {
            if(gallatinOnly==null || !gallatinOnly.contains(collection)) {
                addCollectionToGallatinOnlyList(collection);
            }
        } else {
            if(gallatinOnly.contains(collection)) {
                removeCollectionFromGallatinOnlyList(collection);
            }
        }
    }

}
