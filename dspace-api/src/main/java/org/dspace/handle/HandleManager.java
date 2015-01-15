/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.handle;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.Metadatum;
import org.dspace.content.Site;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;

import cz.cuni.mff.ufal.DSpaceApi;
import cz.cuni.mff.ufal.dspace.content.Handle;
import cz.cuni.mff.ufal.dspace.handle.PIDCommunityConfiguration;
import cz.cuni.mff.ufal.dspace.handle.PIDConfiguration;

/**
 * Interface to the <a href="http://www.handle.net" target=_new>CNRI Handle
 * System </a>.
 *
 * <p>
 * Currently, this class simply maps handles to local facilities; handles which
 * are owned by other sites (including other DSpaces) are treated as
 * non-existent.
 * </p>
 * 
 * based on class by Peter Breton
 * modified for LINDAT/CLARIN
 * @version $Revision$
 */
public class HandleManager
{
    /** log4j category */
    private static Logger log = Logger.getLogger(HandleManager.class);

    /** Prefix registered to no one */
    static final String EXAMPLE_PREFIX = "123456789";

    static final String PREFIX_DELIMITER = "/";
    
    static final String SUBPREFIX_DELIMITER = "-";

    static final String PART_IDENTIFIER_DELIMITER = "@";
    
    static final String HDL_HANDLE_NET_PREFIX_REGEXP = "^(http[s]?://)?hdl.handle.net/";

    /** Private Constructor */
    private HandleManager()
    {
    }

    /**
     * Return the local URL for handle, or null if handle cannot be found.
     *
     * The returned URL is a (non-handle-based) location where a dissemination
     * of the object referred to by handle can be obtained.
     *
     * @param context
     *            DSpace context
     * @param handle
     *            The handle
     * @return The local URL
     * @exception SQLException
     *                If a database error occurs
     */
    public static String resolveToURL(Context context, String handle)
            throws SQLException
    {

        // <UFAL>
        String baseHandle = stripPartIdentifier(handle);
        
        log.debug(String.format("Base handle [%s]", baseHandle));
        
        TableRow dbhandle = findHandleInternal(context, baseHandle);

        if (dbhandle == null)
        {
            return null;
        }

        String url = null;

        if (dbhandle.getStringColumn("url") != null)
        {
            url = dbhandle.getStringColumn("url");
        }
        else
        {
            url = ConfigurationManager.getProperty("dspace.url") + "/handle/"
                    + baseHandle;
        }
        
        String partIdentifier = extractPartIdentifier(handle);
        url = appendPartIdentifierToUrl(url, partIdentifier);
        // </UFAL>

        if (log.isDebugEnabled())
        {
            log.debug("Resolved " + handle + " to " + url);
        }

        return url;
    }
    
    /**
     * Strips the part identifier from the handle
     * 
     * @param handle The handle with optional part identifier 
     * @return The handle without the part identifier
     */
    private static String stripPartIdentifier(String handle) 
    {
        String baseHandle = null;
        if (handle != null)
        {            
            int pos = handle.indexOf(PART_IDENTIFIER_DELIMITER);
            if(pos >= 0)
            {
                baseHandle = handle.substring(0, pos);
            }
            else 
            {
                baseHandle = handle;
            }
        }
        return baseHandle;
    }
    
    /**
     * Extracts the part identifier from the handle
     * 
     * @param handle The handle with optional part identifier
     * @return part identifier or null
     */
    private static String extractPartIdentifier(String handle) 
    {        
        String partIdentifier = null;
        if(handle != null)
        {
            int pos = handle.indexOf(PART_IDENTIFIER_DELIMITER);
            if(pos >= 0)
            {                            
                partIdentifier = handle.substring(pos+1);
            }            
        }        
        return partIdentifier;
    }
    
    /**
     * Appends the partIdentifier as parameters to the given URL
     * 
     * @param url The URL
     * @param partIdentifier  Part identifier (can be null or empty)
     * @return Final URL with part identifier appended as parameters to the given URL
     */
    private static String appendPartIdentifierToUrl(String url, String partIdentifier)
    {
        String finalUrl = url;
        if(finalUrl != null && partIdentifier != null && !partIdentifier.isEmpty())
        {        
            if(finalUrl.contains("?"))
            {
                finalUrl += '&' + partIdentifier;
            }
            else 
            {
                finalUrl += '?' + partIdentifier;
            }
        }        
        return finalUrl;
    }
    /**
     * Try to detect a handle in a URL.
     * @param context DSpace context
     * @param url The URL
     * @return The handle or null if the handle couldn't be extracted of a URL
     * or if the extracted handle couldn't be found.
     * @throws SQLException  If a database error occurs
     */
    public static String resolveUrlToHandle(Context context, String url)
            throws SQLException
    {
        String dspaceUrl = ConfigurationManager.getProperty("dspace.url")
                + "/handle/";
        String handleResolver = ConfigurationManager.getProperty("handle.canonical.prefix");
        
        String handle = null;
        
        if (url.startsWith(dspaceUrl))
        {
            handle = url.substring(dspaceUrl.length());
        }
        
        if (url.startsWith(handleResolver))
        {
            handle = url.substring(handleResolver.length());
        }
        
        if (null == handle)
        {
            return null;
        }
        
        // remove trailing slashes
        while (handle.startsWith("/"))
        {
            handle = handle.substring(1);
        }
        TableRow dbhandle = findHandleInternal(context, handle);
        
        return (null == dbhandle) ? null : handle;
    }

    /**
     * Transforms handle into the canonical form <em>hdl:handle</em>.
     *
     * No attempt is made to verify that handle is in fact valid.
     *
     * @param handle
     *            The handle
     * @return The canonical form
     */
    public static String getCanonicalForm(String handle)
    {

    	// Let the admin define a new prefix, if not then we'll use the
    	// CNRI default. This allows the admin to use "hdl:" if they want to or
    	// use a locally branded prefix handle.myuni.edu.
    	String handlePrefix = ConfigurationManager.getProperty("handle.canonical.prefix");
    	if (handlePrefix == null || handlePrefix.length() == 0)
    	{
    		handlePrefix = "http://hdl.handle.net/";
    	}

    	return handlePrefix + handle;
    }

    /**
     * Returns displayable string of the handle's 'temporary' URL
     * <em>http://hdl.handle.net/handle/em>.
     *
     * No attempt is made to verify that handle is in fact valid.
     *
     * @param handle The handle
     * @return The canonical form
     */

    //    public static String getURLForm(String handle)
    //    {
    //        return "http://hdl.handle.net/" + handle;
    //    }

    /**
     * Creates a new handle in the database.
     *
     * @param context
     *            DSpace context
     * @param dso
     *            The DSpaceObject to create a handle for
     * @return The newly created handle
     * @exception SQLException
     *                If a database error occurs
     */
    public static String createHandle(Context context, DSpaceObject dso)
            throws SQLException, IllegalStateException
    {
        PIDCommunityConfiguration pidCommunityConfiguration = PIDConfiguration
                .getPIDCommunityConfiguration(dso);

        TableRow handle = DatabaseManager.create(context, "Handle");
        String handleId = createHandleId(handle.getIntColumn("handle_id"),
                pidCommunityConfiguration);

        // </UFAL>

        handle.setColumn("handle", handleId);
        handle.setColumn("resource_type_id", dso.getType());
        handle.setColumn("resource_id", dso.getID());
        DatabaseManager.update(context, handle);

        if (log.isDebugEnabled())
        {
            log.debug("Created new handle for "
                    + Constants.typeText[dso.getType()] + " (ID=" + dso.getID()
                    + ") " + handleId);
        }

        return handleId;
    }

    /**
     * Factory method for handle creation
     * 
     * @param id
     * @param pidCommunityConfiguration
     * @return
     */
    private static String createHandleId(int id,
            PIDCommunityConfiguration pidCommunityConfiguration)
    {
        String handleId = null;
        if (pidCommunityConfiguration.isEpic())
        {
            handleId = createEpicHandleId(id, pidCommunityConfiguration);
        }
        else if (pidCommunityConfiguration.isLocal())
        {
            handleId = createLocalHandleId(id, pidCommunityConfiguration);
        }
        else            
        {
            throw new IllegalStateException("Unsupported PID type: "
                    + pidCommunityConfiguration.getType());
        }
        return handleId;
    }

    /**
     * Formats handle suffix
     *
     * @param id Database handle ID (primary key in handle table)
     * @param pidCommunityConfiguration PID Community Configuration
     * @return formatted handle suffix
     */
    private static String formatSuffix(int id, PIDCommunityConfiguration pidCommunityConfiguration)
    {
        StringBuffer suffix = new StringBuffer();
        String handleSubprefix = pidCommunityConfiguration.getSubprefix();
        if(handleSubprefix != null && !handleSubprefix.isEmpty())
        {
            suffix.append(handleSubprefix + SUBPREFIX_DELIMITER);
        }
        suffix.append(id);
        return suffix.toString();
    }

    /**
     * Formats handle
     * 
     * @param id Database handle ID (primary key in handle table)
     * @param pidCommunityConfiguration PID Community Configuration
     * @return formatted handle
     */
    private static String formatHandleID(int id, PIDCommunityConfiguration pidCommunityConfiguration)
    {
        StringBuffer handleId = new StringBuffer();
        String handlePrefix = pidCommunityConfiguration.getPrefix();
        handleId.append(handlePrefix);

        if(!handlePrefix.endsWith(PREFIX_DELIMITER))
        {
            handleId.append(PREFIX_DELIMITER);
        }

        String handleSuffix = formatSuffix(id, pidCommunityConfiguration);
        handleId.append(handleSuffix);
        return handleId.toString();
    }

    /**
     * Creates new handle locally
     *
     * @param id
     *            Database handle ID (primary key in handle table)
     * @param pidCommunityConfiguration
     *            PID Community Configuration
     * @return
     */
    private static String createLocalHandleId(int id,
            PIDCommunityConfiguration pidCommunityConfiguration)
    {
        return formatHandleID(id, pidCommunityConfiguration);
    }

    /**
     * Creates new handle by calling EPIC service
     * 
     * @param id
     *            Database handle ID (primary key in handle table)
     * @param pidCommunityConfiguration
     *            PIC Community Configuration
     * @return
     */
    private static String createEpicHandleId(int id,
            PIDCommunityConfiguration pidCommunityConfiguration)
    {
        String handleId;

        String suffix = formatSuffix(id, pidCommunityConfiguration);
        String prefix = pidCommunityConfiguration.getPrefix();

        try
        {
            handleId = DSpaceApi.handle_HandleManager_createId(log, id, prefix, suffix);
            // if the handle created successfully register the final handle
            DSpaceApi
                    .handle_HandleManager_registerFinalHandleURL(log, handleId);
        }
        catch (IOException e)
        {
            DSpaceApi
                    .getFunctionalityManager()
                    .setErrorMessage(
                            "PID Service is not working. Please contact the administrator.");
            throw new IllegalStateException(
                    "External PID service is not working. Please contact the administrator. "
                            + "Internal message: [" + e.toString() + "]");
        }
        return handleId;
    }

    /**
     * Creates a handle entry, but with a handle supplied by the caller (new
     * Handle not generated)
     *
     * @param context
     *            DSpace context
     * @param dso
     *            DSpaceObject
     * @param suppliedHandle
     *            existing handle value
     * @return the Handle
     * @throws IllegalStateException if specified handle is already in use by another object
     */
    public static String createHandle(Context context, DSpaceObject dso,
            String suppliedHandle) throws SQLException, IllegalStateException
    {
        //Check if the supplied handle is already in use -- cannot use the same handle twice
        TableRow handle = findHandleInternal(context, suppliedHandle);
        if(handle!=null && !handle.isColumnNull("resource_id"))
        {
            //Check if this handle is already linked up to this specified DSpace Object
            if(handle.getIntColumn("resource_id")==dso.getID() &&
               handle.getIntColumn("resource_type_id")==dso.getType())
            {
                //This handle already links to this DSpace Object -- so, there's nothing else we need to do
                return suppliedHandle;
            }
            else
            {
                //handle found in DB table & already in use by another existing resource
                throw new IllegalStateException("Attempted to create a handle which is already in use: " + suppliedHandle);
            }
        }
        else if(handle!=null && !handle.isColumnNull("resource_type_id"))
        {
            //If there is a 'resource_type_id' (but 'resource_id' is empty), then the object using
            // this handle was previously unbound (see unbindHandle() method) -- likely because object was deleted
            int previousType = handle.getIntColumn("resource_type_id");

            //Since we are restoring an object to a pre-existing handle, double check we are restoring the same *type* of object
            // (e.g. we will not allow an Item to be restored to a handle previously used by a Collection)
            if(previousType != dso.getType())
            {
                throw new IllegalStateException("Attempted to reuse a handle previously used by a " +
                        Constants.typeText[previousType] + " for a new " +
                        Constants.typeText[dso.getType()]);
            }
        }
        else if(handle==null) //if handle not found, create it
        {
            //handle not found in DB table -- create a new table entry
            handle = DatabaseManager.create(context, "Handle");
            handle.setColumn("handle", suppliedHandle);
        }

        handle.setColumn("resource_type_id", dso.getType());
        handle.setColumn("resource_id", dso.getID());
        DatabaseManager.update(context, handle);

        if (log.isDebugEnabled())
        {
            log.debug("Created new handle for "
                    + Constants.typeText[dso.getType()] + " (ID=" + dso.getID() + ") " + suppliedHandle );
        }

        return suppliedHandle;
    }

    /**
     * Removes binding of Handle to a DSpace object, while leaving the
     * Handle in the table so it doesn't get reallocated.  The AIP
     * implementation also needs it there for foreign key references.
     *
     * @param context DSpace context
     * @param dso DSpaceObject whose Handle to unbind.
     */
    public static void unbindHandle(Context context, DSpaceObject dso)
        throws SQLException
    {
        TableRowIterator rows = getInternalHandles(context, dso.getType(), dso.getID());
        if (rows != null)
        {
            while (rows.hasNext())
            {
                TableRow row = rows.next();
                //Only set the "resouce_id" column to null when unbinding a handle.
                // We want to keep around the "resource_type_id" value, so that we
                // can verify during a restore whether the same *type* of resource
                // is reusing this handle!
                row.setColumnNull("resource_id");
                DatabaseManager.update(context, row);

                if(log.isDebugEnabled())
                {
                    log.debug("Unbound Handle " + row.getStringColumn("handle") + " from object " + Constants.typeText[dso.getType()] + " id=" + dso.getID());
                }
            }
        }
        else
        {
            log.warn("Cannot find Handle entry to unbind for object " + Constants.typeText[dso.getType()] + " id=" + dso.getID());
        }
    }

    /**
     * Return the object which handle maps to, or null. This is the object
     * itself, not a URL which points to it.
     *
     * @param context
     *            DSpace context
     * @param handle
     *            The handle to resolve
     * @return The object which handle maps to, or null if handle is not mapped
     *         to any object.
     * @exception IllegalStateException
     *                If handle was found but is not bound to an object
     * @exception SQLException
     *                If a database error occurs
     */
    public static DSpaceObject resolveToObject(Context context, String handle)
            throws IllegalStateException, SQLException
    {
        TableRow dbhandle = findHandleInternal(context, handle);

        if (dbhandle == null)
        {
            //If this is the Site-wide Handle, return Site object
            if (handle.equals(Site.getSiteHandle()))
            {
                return Site.find(context, 0);
            }
            //Otherwise, return null (i.e. handle not found in DB)
            return null;
        }

        // check if handle was allocated previously, but is currently not
        // associated with a DSpaceObject
        // (this may occur when 'unbindHandle()' is called for an obj that was removed)
        if ((dbhandle.isColumnNull("resource_type_id"))
                || (dbhandle.isColumnNull("resource_id")))
        {
            //if handle has been unbound, just return null (as this will result in a PageNotFound)
            return null;
        }

        // What are we looking at here?
        int handletypeid = dbhandle.getIntColumn("resource_type_id");
        int resourceID = dbhandle.getIntColumn("resource_id");

        if (handletypeid == Constants.ITEM)
        {
            Item item = Item.find(context, resourceID);

            if (log.isDebugEnabled())
            {
                log.debug("Resolved handle " + handle + " to item "
                        + ((item == null) ? (-1) : item.getID()));
            }

            return item;
        }
        else if (handletypeid == Constants.COLLECTION)
        {
            Collection collection = Collection.find(context, resourceID);

            if (log.isDebugEnabled())
            {
                log.debug("Resolved handle " + handle + " to collection "
                        + ((collection == null) ? (-1) : collection.getID()));
            }

            return collection;
        }
        else if (handletypeid == Constants.COMMUNITY)
        {
            Community community = Community.find(context, resourceID);

            if (log.isDebugEnabled())
            {
                log.debug("Resolved handle " + handle + " to community "
                        + ((community == null) ? (-1) : community.getID()));
            }

            return community;
        }

        throw new IllegalStateException("Unsupported Handle Type "
                + Constants.typeText[handletypeid]);
    }

    /**
     * Return the handle for an Object, or null if the Object has no handle.
     *
     * @param context
     *            DSpace context
     * @param dso
     *            The object to obtain a handle for
     * @return The handle for object, or null if the object has no handle.
     * @exception SQLException
     *                If a database error occurs
     */
    public static String findHandle(Context context, DSpaceObject dso)
            throws SQLException
    {
        TableRowIterator rows = getInternalHandles(context, dso.getType(), dso.getID());
        if (rows == null || !rows.hasNext())
        {
            if (dso.getType() == Constants.SITE)
            {
                return Site.getSiteHandle();
            }
            else
            {
                return null;
            }
        }
        else
        {
            //TODO: Move this code away from the HandleManager & into the Identifier provider
            //Attempt to retrieve a handle that does NOT look like {handle.part}/{handle.part}.{version}
            String result = rows.next().getStringColumn("handle");
            while (rows.hasNext())
            {
                TableRow row = rows.next();
                //Ensure that the handle doesn't look like this 12346/213.{version}
                //If we find a match that indicates that we have a proper handle
                if(!row.getStringColumn("handle").matches(".*/.*\\.\\d+"))
                {
                    result = row.getStringColumn("handle");
                }
            }

            return result;
        }
    }

    /**
     * Return all the handles which start with prefix.
     *
     * @param context
     *            DSpace context
     * @param prefix
     *            The handle prefix
     * @return A list of the handles starting with prefix. The list is
     *         guaranteed to be non-null. Each element of the list is a String.
     * @exception SQLException
     *                If a database error occurs
     */
    public static List<String> getHandlesForPrefix(Context context, String prefix)
            throws SQLException
    {
        String sql = "SELECT handle FROM handle WHERE handle LIKE ? ";
        TableRowIterator iterator = DatabaseManager.queryTable(context, null, sql, prefix+"%");
        List<String> results = new ArrayList<String>();

        try
        {
            while (iterator.hasNext())
            {
                TableRow row = (TableRow) iterator.next();
                results.add(row.getStringColumn("handle"));
            }
        }
        finally
        {
            // close the TableRowIterator to free up resources
            if (iterator != null)
            {
                iterator.close();
            }
        }

        return results;
    }

    /**
     * Get the configured Handle prefix string, or a default
     * @return configured prefix or "123456789"
     */
    public static String getPrefix()
    {
        String prefix = PIDConfiguration.getDefaultPrefix();                
        if (null == prefix)
        {
            prefix = EXAMPLE_PREFIX; // XXX no good way to exit cleanly
            log.error("default handle prefix is not configured; using " + prefix);
        }
        return prefix;
    }

    ////////////////////////////////////////
    // Internal methods
    ////////////////////////////////////////

    /**
     * Return the handle for an Object, or null if the Object has no handle.
     *
     * @param context
     *            DSpace context
     * @param type
     *            The type of object
     * @param id
     *            The id of object
     * @return The handle for object, or null if the object has no handle.
     * @exception SQLException
     *                If a database error occurs
     */
    private static TableRowIterator getInternalHandles(Context context, int type, int id)
            throws SQLException
    {
      	String sql = "SELECT * FROM Handle WHERE resource_type_id = ? " +
      				 "AND resource_id = ?";

	return DatabaseManager.queryTable(context, "Handle", sql, type, id);
    }

    /**
     * Find the database row corresponding to handle.
     *
     * @param context
     *            DSpace context
     * @param handle
     *            The handle to resolve
     * @return The database row corresponding to the handle
     * @exception SQLException
     *                If a database error occurs
     */
    private static TableRow findHandleInternal(Context context, String handle)
            throws SQLException
    {
        if (handle == null)
        {
            throw new IllegalArgumentException("Handle is null");
        }

        return DatabaseManager
                .findByUnique(context, "Handle", "handle", handle);
    }

    /**
     * Create a new handle id. The implementation uses the PK of the RDBMS
     * Handle table.
     *
     * @return A new handle id
     * @exception SQLException
     *                If a database error occurs
     */
    private static String createId(int id) throws SQLException
    {
        String handlePrefix = getPrefix();

        return new StringBuffer()
                .append(handlePrefix)
                .append(handlePrefix.endsWith(PREFIX_DELIMITER) ? ""
                        : PREFIX_DELIMITER).append(id).toString();
    }

    /**
     * List all distinct prefixes stored in Handle table.
     * 
     * @param context
     *            DSpace context
     * @return Alphabetically sorted list of handle prefixes
     * @exception SQLException
     *                If a database error occurs
     */
    public static List<String> getPrefixes(Context context) throws SQLException
    {
        List<String> prefixes = new ArrayList<String>();
        String sql = "select distinct regexp_replace(handle,'^([^/]*).*$','\\\\1') as prefix from handle where handle	 like '%/%' order by prefix";

        TableRowIterator iterator = DatabaseManager.query(context, sql);

        try
        {
            while (iterator.hasNext())
            {
                TableRow row = (TableRow) iterator.next();
                prefixes.add(row.getStringColumn("prefix"));
            }
        }
        finally
        {
            // close the TableRowIterator to free up resources
            if (iterator != null)
            {
                iterator.close();
            }
        }

        return prefixes;
    }

    /**
     * Changes the specified handle
     * 
     * @param context
     *            DSpace context
     * @param oldHandle
     *            Old handle
     * @param newHandle
     *            New handle
     * @param archiveOldHandle
     *            Flag indicating whether the old handle should be stored in
     *            Item metadata
     * @throws Exception
     */
    public static void changeHandle(Context context, String oldHandle,
            String newHandle, boolean archiveOldHandle) throws SQLException,
            AuthorizeException
    {
        // Ignore invalid handles
        if(oldHandle == null)
        {
            return;
        }

        // Find handle
        Handle h = Handle.findByHandle(context, oldHandle);

        if (h == null)
        {
            throw new IllegalStateException("Handle " + oldHandle
                    + "not found ");
        }

        Item item = null;

        if (h.isInternalResource())
        {
            // Try resolving handle to Item
            try
            {
                DSpaceObject dso = null;
                dso = resolveToObject(context, oldHandle);
                if (dso != null && dso.getType() == Constants.ITEM)
                {
                    item = (Item) dso;
                }
            }
            catch (IllegalStateException e)
            {
                item = null;
            }

            if (item != null)
            {
                // Handle resolved to Item
                if (archiveOldHandle)
                {
                    // Archive metadata
                    Metadatum[] dcUri = item.getMetadata("dc", "identifier",
                            "uri", Item.ANY);

                    List<String> values = new ArrayList<String>();

                    for (int i = 0; i < dcUri.length; i++)
                    {
                        values.add(dcUri[i].value);
                    }

                    item.addMetadata("dc", "identifier", "other", Item.ANY,
                            values.toArray(new String[values.size()]));
                }

                item.clearMetadata("dc", "identifier", "uri", Item.ANY);

                if (newHandle != null && !newHandle.isEmpty())
                {
                    // Update dc.identifier.uri
                    String newUri = getCanonicalForm(newHandle);
                    item.addMetadata("dc", "identifier", "uri", Item.ANY,
                            newUri);
                }

                // Update the metadata
                item.update();
            }
        }

        // Update the handle itself
        // - needs to be done before archiving handle to avoid unique constraint
        // violation
        h.setHandle(newHandle);
        h.update();

        if (archiveOldHandle)
        {
            if (!newHandle.equals(oldHandle))
            {
                // Archive handle
                String newUrl = resolveToURL(context, newHandle);
                Handle archiveHandle = Handle.create(context, null, oldHandle);
                archiveHandle.setURL(newUrl);
                archiveHandle.update();
            }
        }

    }

    /**
     * Changes handle prefix
     * 
     * @param context
     *            DSpace context
     * @param oldPrefix
     *            Old handle prefix
     * @param newPrefix
     *            New handle prefix
     * @param archiveOldHandles
     *            Flag indicating whether the old handle should be stored in
     *            Item metadata
     * @throws Exception
     */
    public static void changePrefix(Context context, String oldPrefix,
            String newPrefix, boolean archiveOldHandles) throws Exception
    {
        // Iterates over the list of
        String sql = "select handle as old_handle, regexp_replace(handle,'^([^/]*)(/.*)$', ? || '\\2') as new_handle from handle"
                + " where handle like ? || '/%' order by handle_id";

        TableRowIterator iterator = DatabaseManager.query(context, sql,
                newPrefix, oldPrefix);

        try
        {
            while (iterator.hasNext())
            {
                TableRow row = (TableRow) iterator.next();
                String oldHandle = row.getStringColumn("old_handle");
                String newHandle = row.getStringColumn("new_handle");
                changeHandle(context, oldHandle, newHandle, archiveOldHandles);
            }
        }
        finally
        {
            // close the TableRowIterator to free up resources
            if (iterator != null)
            {
                iterator.close();
            }
        }
    }
    
    /**
     * Returns prefix of a given handle
     * 
     * @param handle Prefix of the handle
     */
    public static String extractHandlePrefix(String handle)
    {
        String prefix = null;
        if(handle != null)
        {
            int pos = handle.indexOf(PREFIX_DELIMITER);
            if(pos >= 0)
            {
                prefix = handle.substring(0, pos);
            }
        }
        return prefix;
    }
    
    /**
     * Returns suffix of a given handle
     * 
     * @param handle Suffix of the handle
     */
    public static String extractHandleSuffix(String handle)
    {
        String suffix = null;
        if(handle != null)
        {
            int pos = handle.indexOf(PREFIX_DELIMITER);
            if(pos >= 0)
            {
                suffix = handle.substring(pos+1);
            }
        }
        return suffix;
    }
    
    /**
     * Returns complete handle made from prefix and suffix
     * 
     * @param handle Complete handle
     */
    public static String completeHandle(String prefix, String suffix)
    {        
        return prefix + PREFIX_DELIMITER + suffix;
    }
        
    /**
     * Returns true if the prefix of the given handle is supported by this repository
     *  
     * @return True if the prefix of the given handle is supported by this repository
     */
    public static boolean hasHandleSupportedPrefix(String handle)
    {            
        String prefix = extractHandlePrefix(handle); 
        Set<String> supportedPrefixes = PIDConfiguration.getSupportedPrefixes();
        return supportedPrefixes.contains(prefix);
    }
    
    /**
     * Returns true if the prefix is supported by this repository
     *  
     * @return True if the prefix is supported by this repository
     */
    public static boolean isSupportedPrefix(String prefix)
    {                    
        Set<String> supportedPrefixes = PIDConfiguration.getSupportedPrefixes();
        return supportedPrefixes.contains(prefix);
    }
    
    /**
     * Strips hdl.handle.net prefix from a given URL
     * @param url URL starting with something like http://hdl.handle.net/
     * @return True if URL contains the hdl.handle.net prefix
     */
    public static String stripHdlHandleNetPrefixFromURL(String url) 
    {
        return url.replaceAll(HDL_HANDLE_NET_PREFIX_REGEXP,"");
    }
    
    /**
     * Returns true if URL contains hdl.handle.net prefix
     * @param url URL starting with something like http://hdl.handle.net/
     * @return Remaining string (possibly the handle)
     */
    public static boolean hasURLHdlHandleNetPrefix(String url) 
    {
        return url.matches(HDL_HANDLE_NET_PREFIX_REGEXP);
    }

}
