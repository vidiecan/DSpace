/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package cz.cuni.mff.ufal.dspace.app.xmlui.aspect.administrative;

import java.io.IOException;
import java.sql.SQLException;

import org.apache.avalon.framework.activity.Disposable;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.avalon.framework.service.ServiceManager;
import org.apache.avalon.framework.service.Serviceable;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.configuration.Settings;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Request;
import org.apache.excalibur.store.Store;
import org.apache.excalibur.store.StoreJanitor;
import org.dspace.app.xmlui.cocoon.AbstractDSpaceTransformer;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.Body;
import org.dspace.app.xmlui.wing.element.Division;
import org.dspace.app.xmlui.wing.element.List;
import org.dspace.app.xmlui.wing.element.PageMeta;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.PluginManager;
import org.xml.sax.SAXException;

/**
 * This page displays important information about running dspace.
 *
 * based on class by Jay Paz and Scott Phillips
 * modified for LINDAT/CLARIN
 * @author Amir Kamran
 */
public class ControlPanel extends AbstractDSpaceTransformer implements Serviceable, Disposable {

    /** Language Strings */
    private static final Message T_DSPACE_HOME		= message("xmlui.general.dspace_home");
    private static final Message T_title			= message("xmlui.administrative.ControlPanel.title");
    private static final Message T_trail			= message("xmlui.administrative.ControlPanel.trail");
    private static final Message T_head				= message("xmlui.administrative.ControlPanel.head");
    
    private static final Message T_select_panel		= message("xmlui.administrative.ControlPanel.select_panel");


    /** 
     * The service manager allows us to access the continuation's 
     * manager.  It is obtained from the Serviceable API
     */
    private ServiceManager serviceManager;

    /**
     * The Cocoon Settings (used to display Cocoon info)
     */
    private Settings settings;

    /**
     * The Cocoon StoreJanitor (used for cache statistics)
     */
    private StoreJanitor storeJanitor;

     /**
     * The Cocoon Default Store (used for cache statistics)
     */
    private Store storeDefault;

    /**
     * The Cocoon Persistent Store (used for cache statistics)
     */
    private Store storePersistent;
    
    /**
     * From the <code>org.apache.avalon.framework.service.Serviceable</code> API, 
     * give us the current <code>ServiceManager</code> instance.
     * <P>
     * Much of this ServiceManager logic/code has been borrowed from the source
     * code of the Cocoon <code>StatusGenerator</code> class:
     * http://svn.apache.org/repos/asf/cocoon/tags/cocoon-2.2/cocoon-sitemap-components/cocoon-sitemap-components-1.0.0/src/main/java/org/apache/cocoon/generation/StatusGenerator.java
     */
    @Override
    public void service(ServiceManager serviceManager) throws ServiceException 
    {
        this.serviceManager = serviceManager;
        
        this.settings = (Settings) this.serviceManager.lookup(Settings.ROLE);
        
        if(this.serviceManager.hasService(StoreJanitor.ROLE))
            this.storeJanitor = (StoreJanitor) this.serviceManager.lookup(StoreJanitor.ROLE);
        
        if (this.serviceManager.hasService(Store.ROLE))
            this.storeDefault = (Store) this.serviceManager.lookup(Store.ROLE);
        
        if(this.serviceManager.hasService(Store.PERSISTENT_STORE))
            this.storePersistent = (Store) this.serviceManager.lookup(Store.PERSISTENT_STORE);
    }

    @Override
    public void addPageMeta(PageMeta pageMeta) throws SAXException,
                    WingException, UIException, SQLException, IOException,
                    AuthorizeException 
    {
        pageMeta.addMetadata("title").addContent(T_title);
        pageMeta.addMetadata("include-library", "controlpanel");

        pageMeta.addTrailLink(contextPath + "/", T_DSPACE_HOME);
        pageMeta.addTrailLink(null, T_trail);
    }

    @Override
    public void addBody(Body body) throws SAXException, WingException,
                    UIException, SQLException, IOException, AuthorizeException 
    {

        if (!AuthorizeManager.isAdmin(context))
        {
            throw new AuthorizeException("You are not authorized to view this page.");
        }

        Division div = body.addInteractiveDivision("control-panel", contextPath+"/admin/panel", Division.METHOD_POST, "primary administrative");
        div.setHead(T_head);

        Request request = ObjectModelHelper.getRequest(objectModel);
        String selected_tab = "";

        if (request.getParameter("tab") != null)
        {
        	selected_tab = request.getParameter("tab");
        	div.addHidden("tab").setValue(selected_tab);
        }

        // LIST: options
        List options = div.addList("options", List.TYPE_SIMPLE, "horizontal");
        
        String tabs[] = ConfigurationManager.getProperty("controlpanel", "controlpanel.tabs").split(",");
        
        for(String tab : tabs) {
        	tab = tab.trim();
        	if(tab.equals(selected_tab)) {
        		options.addItem().addHighlight("bold").addXref("?tab=" + selected_tab, selected_tab);
        	} else {
        		options.addItemXref(contextPath + "/admin/panel?tab=" + tab, tab);
        	}
        }
        
        if(selected_tab.equals("")) {
        	div.addPara(T_select_panel);
        } else {
        	ControlPanelTab cpTab = (ControlPanelTab)PluginManager.getNamedPlugin("controlpanel", ControlPanelTab.class, selected_tab);
        	if(cpTab instanceof AbstractControlPanelTab) {
        		try {
        			((AbstractControlPanelTab) cpTab).setup(null, objectModel, null, parameters);
					((AbstractControlPanelTab) cpTab).service(serviceManager);
					((AbstractControlPanelTab) cpTab).setWebLink(contextPath + "/admin/panel?tab=" + selected_tab);
				} catch (ServiceException e) {
					e.printStackTrace();
				} catch (ProcessingException e) {
					e.printStackTrace();
				}
        	}        	
        	cpTab.addBody(objectModel, div);
        }        
    }

    
    /**
     * Release all Cocoon resources.
     * @see org.apache.avalon.framework.activity.Disposable#dispose()
     */
    @Override
    public void dispose() 
    {
        if (this.serviceManager != null) 
        {
            this.serviceManager.release(this.storePersistent);
            this.serviceManager.release(this.storeJanitor);
            this.serviceManager.release(this.storeDefault);
            this.serviceManager.release(this.settings);
            this.storePersistent = null;
            this.storeJanitor = null; 	
            this.storeDefault = null;
            this.settings = null;
        }
        super.dispose();
    }
}



