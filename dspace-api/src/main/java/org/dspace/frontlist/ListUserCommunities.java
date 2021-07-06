/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 * <p>
 * http://www.dspace.org/license/
 */
package org.dspace.frontlist;


import java.sql.SQLException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentMap;

import org.apache.log4j.Logger;

import org.dspace.content.Community;
import org.dspace.content.Collection;
import org.dspace.content.DSpaceObject;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;

/**
 * This class generates list of collections and communities which will be listed on home page and on community pages
 * Anonymous users can only see - public, NYU only and school specific (Gallatin) collection which are not empty.
 * Collection, community and site admins can see empty and private collections which they administrate. Also submitters
 * can see empty collections to which they can add items.
 * We make all initial calculations when jspui application starts and then do updates when changes are made:
 * when items are added to empty collection,
 * when all items are removed from collection,
 * when new admins are added or removed for private collections,
 * when collection permissions are modified,
 * when collections and communities are added or removed
 *
 * @author Kate Pechekhonova
 *
 */
public class ListUserCommunities {


    // This contains list of NYU only collections IDs
    public static CopyOnWriteArrayList<Integer> nyuOnly;

    // This contains list of Gallatin only collectiuons IDs
    public static CopyOnWriteArrayList<Integer> gallatinOnly;

    // This contains list of empty collections IDs
    public static CopyOnWriteArrayList<Integer> emptyCollections;

    // This contains list of private collections IDs
    public static CopyOnWriteArrayList<Integer> privateCollections;

    // This will map parent communityIDs to arrays of collections for all
    // publicly available (not private and not empty) collections
    public static ConcurrentMap<Integer, Collection[]> colMapAnon;

    // This will map parent communityIDs to arrays of sub-communities for all
    // publicly available (not private and not empty) collections
    public static ConcurrentMap<Integer, Community[]> commMapAnon;

    // This will map all parent communityIDs to arrays of all  collections
    public static ConcurrentMap<Integer, Collection[]> colMapAdmin;

    // This will map all parent communityIDs to arrays of all sub-communities
    public static ConcurrentMap<Integer, Community[]> commMapAdmin;

    // This contains final list of triples <epersonID,groupID,collectionID> for empty and private collection admins
    public static CopyOnWriteArrayList<AuthorizedCollectionUsers> colAuthorizedUsers;

    // This contains final list of triples <epersonID,groupID,communityID> for empty and private community admins
    public static CopyOnWriteArrayList<AuthorizedCommunityUsers> commAuthorizedUsers;

    // This contains temporary list of triples <epersonID,groupID,collectionID> for empty and private collection admins
    // It will be used for initial calculations as CopyOnWriteArrayList creates new copy each time "write" operation is performed
    // so is not suitable for many inserts
    public static ArrayList<AuthorizedCollectionUsers> colAuthorizedUsersRaw;

    // This contains temporary list of triples <epersonID,groupID,communityID> for empty and private community admins
    // It will be used for initial calculations as CopyOnWriteArrayList creates new copy each time "write" operation is performed
    // so is not suitable for many inserts
    public static ArrayList<AuthorizedCommunityUsers> commAuthorizedUsersRaw;


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

    //generates maps for anon user, for site admins and other strucrtures which will be used to get tailored community lists for different users
    public static synchronized void PrebuildFrontListsCommunities() throws java.sql.SQLException {

        Context context = new Context();

        if( colMapAnon==null && commMapAnon==null) {

            colMapAnon = new ConcurrentHashMap<Integer, Collection[]>();
            commMapAnon = new ConcurrentHashMap<Integer, Community[]>();
            colMapAdmin = new ConcurrentHashMap<Integer, Collection[]>();
            commMapAdmin = new ConcurrentHashMap<Integer, Community[]>();
            colAuthorizedUsersRaw = new ArrayList<AuthorizedCollectionUsers>();
            commAuthorizedUsersRaw = new ArrayList<AuthorizedCommunityUsers>();
            colAuthorizedUsers = new CopyOnWriteArrayList<AuthorizedCollectionUsers>();
            commAuthorizedUsers = new CopyOnWriteArrayList<AuthorizedCommunityUsers>();
            nyuOnly = new  CopyOnWriteArrayList<Integer>();
            gallatinOnly = new  CopyOnWriteArrayList<Integer>();
            privateCollections = new  CopyOnWriteArrayList<Integer>();
            emptyCollections = new  CopyOnWriteArrayList<Integer>();

            // Build admin and general maps of collection for each  community.
            // In the process of building maps we will also build nyuOnly, gallatinOnly,
            // privateCollections, emptyCollections which will allow us to make faster calculations
            // We also will build an ArrayList of class AuthorizedCoolectionUsers
            // The later will be used to build user specific front list for collection admins
            Community[] communities = Community.findAll(context);

            if(communities!=null) {
                //user only can see non-empty, not private collections
                for (int com = 0; com < communities.length; com++) {
                    buildCollections(communities[com]);
                }
            }

            //Build admin and general list of subcommunities for each community.
            //In the process of building maps we will also build ArrayList of class AuthorizedCommunityUsers.
            // It will be used to build user specific front list for community admins.
            // We will be using recrussion so here we start with Top level communities
            Community[] communitiesAval = Community.findAllTop(context);

            if(communitiesAval!=null) {
                for (int com = 0; com < communitiesAval.length; com++) {
                    buildCommunity(communitiesAval[com]);
                }
            }

            //convert ArrayLists for private and empty collections/communities admins to CopyOnWriteArrayList to make it threadsafe
              colAuthorizedUsers = new CopyOnWriteArrayList<AuthorizedCollectionUsers>(colAuthorizedUsersRaw);
              commAuthorizedUsers = new CopyOnWriteArrayList<AuthorizedCommunityUsers>(commAuthorizedUsersRaw);
        }

    }

    /* Build admin and general maps of collection for each  community.
     * In the process of building maps we will also build nyuOnly, gallatinOnly,
     * privateCollections, emptyCollections which will allow us to make faster calculations
     * We also will build an ArrayList of class AuthorizedCoolectionUsers
     * The later will be used to build user specific front list for collection admins
     * Takes parent community as input
     */
    private static void buildCollections(Community c) throws SQLException {

        Integer comID = c.getID();
        Collection[] colls = c.getCollections();
        log.debug("collection array length: "+colls.length);

        //build admin map
        colMapAdmin.put(comID, colls);
        log.debug("community used: "+c.getName()+" size: "+ colMapAdmin.get(comID).length);

        if (colls.length > 0) {
            ArrayList<Collection> availableCol = new ArrayList<Collection>();


            for (int i = 0; i < colls.length; i++) {

                int countItems = colls[i].countItems();
                int colID = colls[i].getID();
                if (colls[i].isPublic()) {
                    if(countItems>0) {
                        availableCol.add(colls[i]);
                    } else {
                        emptyCollections.add(colID);
                    }
                } else {
                    if (colls[i].isNYUOnly()) {
                        if(countItems>0) {
                            availableCol.add(colls[i]);
                        } else {
                            emptyCollections.add(colID);
                        }
                        nyuOnly.add(colID);

                    } else {

                        if (colls[i].isGallatin()) {
                            if(countItems>0) {
                                availableCol.add(colls[i]);
                            } else {
                                emptyCollections.add(colID);
                            }
                            gallatinOnly.add(colID);
                        }
                    }
                }

                if (!availableCol.contains(colls[i])) {

                    buildAuthorizedColList(colls[i]);

                    if(colls[i].isPrivate()) {
                        privateCollections.add(colID);
                    }
                    if(countItems==0) {
                        emptyCollections.add(colID);
                    }

                }
            }
            //if community has publicly available collection add it to generic map
            if (availableCol.size() > 0) {
                Collection[] availableColArray = new Collection[availableCol.size()];
                colMapAnon.put(comID, availableCol.toArray(availableColArray));
            }
        }

    }

    private static void buildCommunity(Community c) throws SQLException {
        Integer comID = c.getID();
        Community[] comms = c.getSubcommunities();
        log.debug("community array length: "+comms.length);

        commMapAdmin.put(comID, comms);
        log.debug("community: "+c.getName()+" size: "+ commMapAdmin.get(comID).length);

        if (comms.length > 0) {
            ArrayList<Community> availableComm = new ArrayList<Community>();

            for (int i = 0; i < comms.length; i++) {

                buildCommunity(comms[i]);

                if (colMapAnon.containsKey(comms[i].getID())
                        || commMapAnon.containsKey(comms[i].getID())) {
                    availableComm.add(comms[i]);
                //if community has only private and non-empty collections or subcommunities we need to add it's admins to
                //authorized community admins list so they can see it in the list
                } else {
                    buildAuthorizedCommList(comms[i]);
                }
            }
            //if community has public,nyuonly,gallatinonly and non-empty collections or subcommunities we need to add them to generic list
            if (availableComm.size() > 0) {
                Community[] availableCommArray = new Community[availableComm.size()];
                commMapAnon.put(comID, availableComm.toArray(availableCommArray));
            }
        }
    }

    //get all users who might have admin access to the collection
    //they might be collection admins or submitters or parent community admins
    //take collection as parameter
    private static void buildAuthorizedColList(Collection col) throws java.sql.SQLException {

        Group admins = col.getAdministrators();
        if(admins!=null) {
              buildAuthorizedGroupUsers(admins,col);
        }

        Group submitters = col.getSubmitters();
        if(submitters!=null) {
              buildAuthorizedGroupUsers(submitters,col);
        }

        Community[] parentComms = col.getCommunities();
        for(Community  parentComm:parentComms) {
              buildCommGroupUsers(parentComm,col);
        }

    }

    //get all users who might have admin access to the community
    //they might be collection admins or submitters or parent community admins
    //takes community as a parameter
    private static void buildAuthorizedCommList(Community com) throws java.sql.SQLException {

        Group admins = com.getAdministrators();
        if(admins!=null) {
            buildAuthorizedGroupUsers(admins,com);
        }
    }

    //get all admins for collection's parent community
    private static void buildCommGroupUsers(Community com,Collection col) throws SQLException {

        Group admins = com.getAdministrators();
        if(admins!=null) {
            buildAuthorizedGroupUsers(admins,col);
        }
    }

    //add values to colAuthorizedUsersRaw and commAuthorizedUsersRaw ArrayLists which will be at the end converted to WriteOnCopyArrayLists
    //take the group assosiated with object and either collection or community
    private static void buildAuthorizedGroupUsers(Group g,DSpaceObject ds) {

        if(ds.getType()==Constants.COLLECTION) {
            for (EPerson eperson : g.getMembers()) {
                colAuthorizedUsersRaw.add(new AuthorizedCollectionUsers(eperson.getID(), g.getID(), ds.getID()));
            }
            for (Group ag : g.getMemberGroups()) {
                for (EPerson geperson : ag.getMembers()) {
                    colAuthorizedUsersRaw.add(new AuthorizedCollectionUsers(geperson.getID(), g.getID(), ds.getID()));
                }

            }
        }

        if(ds.getType()==Constants.COMMUNITY) {
            for (EPerson eperson : g.getMembers()) {
                colAuthorizedUsersRaw.add(new AuthorizedCollectionUsers(eperson.getID(), g.getID(), ds.getID()));
            }
            for (Group ag : g.getMemberGroups()) {
                for (EPerson geperson : ag.getMembers()) {
                    commAuthorizedUsers.add(new AuthorizedCommunityUsers(geperson.getID(), g.getID(), ds.getID()));
                }

            }
        }
    }

    //Returns array of private and empty collection's ids for which user has admin access.
    //Takes epersonId as a parameter and get data from static CopyOnWriteArrayList<AuthorizedCollectionUsers> colAuthorizedUsers
    public static ArrayList getAuthorizedCollections(int epersonID) {
        ArrayList colIDs = new ArrayList();
        Iterator iteratorAuthCollections = colAuthorizedUsers.iterator();
        while (iteratorAuthCollections.hasNext()) {
            AuthorizedCollectionUsers authCol = (AuthorizedCollectionUsers) iteratorAuthCollections.next();
            if(authCol.getEpersonID()==epersonID) {
                    colIDs.add(authCol.getCollectionID());
            }

        }
        return colIDs;
    }

    //Returns if user has admin access to any private or empty collection.
    //Takes epersonId as a parameter and get data from static CopyOnWriteArrayList<AuthorizedCollectionUsers> colAuthorizedUsers
    public static Boolean checkAuthorizedCollections(int epersonID) {
        ArrayList colIDs = new ArrayList();
        Iterator iteratorAuthCollections = colAuthorizedUsers.iterator();
        while (iteratorAuthCollections.hasNext()) {
            AuthorizedCollectionUsers authCol = (AuthorizedCollectionUsers) iteratorAuthCollections.next();
            if(authCol.getEpersonID()==epersonID) {
                return true;
            }

        }
        return false;
    }

    //Returns array of private and empty subcommunities ids for which user has admin access.
    //Takes epersonId as a parameter and get data from static CopyOnWriteArrayList<AuthorizedCommunityUsers> commAuthorizedUsers
    public static  ArrayList getAuthorizedCommunities(int epersonID)  {
        ArrayList comIDs = new ArrayList();
        Iterator iteratorAuthCommunities = commAuthorizedUsers.iterator();
        while (iteratorAuthCommunities.hasNext()) {
            AuthorizedCommunityUsers authComm = (AuthorizedCommunityUsers) iteratorAuthCommunities.next();
            if(authComm.getEpersonID()==epersonID) {
                    comIDs.add(authComm.getCollectionID());
            }

        }
        return comIDs;
    }

    //Returns array of private and empty subcommunities ids for which user has admin access.
    //Takes epersonId as a parameter and get data from static CopyOnWriteArrayList<AuthorizedCommunityUsers> commAuthorizedUsers
    public static Boolean checkAuthorizedCommunities(int epersonID)  {
        ArrayList comIDs = new ArrayList();
        Iterator iteratorAuthCommunities = commAuthorizedUsers.iterator();
        while (iteratorAuthCommunities.hasNext()) {
            AuthorizedCommunityUsers authComm = (AuthorizedCommunityUsers) iteratorAuthCommunities.next();
            if(authComm.getEpersonID()==epersonID) {
                return true;
            }

        }
        return false;
    }

    /*private static ArrayList<EPerson> getAuthirizedCollectionUsers(Collection col) throws SQLException {

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

   /* public static synchronized void addCollectionToPrivateList(Collection col) throws java.sql.SQLException {


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

    public static  void addAuthorizedUser(DSpaceObject dso, EPerson eperson) {
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

    public static  void removeAuthorizedUser(DSpaceObject dso, EPerson eperson) {
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

    public static  void updateCollectionMetadata( Collection col, Boolean admin ) throws java.sql.SQLException {

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

    public static  void updateCommunityMetadata( Community com, Boolean admin ) throws java.sql.SQLException {
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
    }*/

}

