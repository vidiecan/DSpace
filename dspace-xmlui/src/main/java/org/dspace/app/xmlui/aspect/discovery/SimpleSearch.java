/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.xmlui.aspect.discovery;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.cocoon.caching.CacheableProcessingComponent;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Request;
import org.apache.commons.lang.StringUtils;
import org.dspace.app.xmlui.cocoon.AbstractDSpaceTransformer;
import org.dspace.app.xmlui.utils.HandleUtil;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.*;
import org.dspace.app.xmlui.wing.element.Item;
import org.dspace.app.xmlui.wing.element.List;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.*;
import org.dspace.core.ConfigurationManager;
import org.dspace.discovery.*;
import org.dspace.discovery.configuration.DiscoveryConfiguration;
import org.dspace.discovery.configuration.DiscoverySearchFilter;
import org.dspace.utils.DSpace;
import org.xml.sax.SAXException;

/**
 * Perform a simple search of the repository. The user provides a simple one
 * field query (the url parameter is named query) and the results are processed.
 *
 * @author Kevin Van de Velde (kevin at atmire dot com)
 * @author Mark Diggory (markd at atmire dot com)
 * @author Ben Bosman (ben at atmire dot com)
 * @author Adán Román Ruiz <aroman@arvo.es> (Bugfix)
 * modified for LINDAT/CLARIN
 */
public class SimpleSearch extends AbstractSearch implements CacheableProcessingComponent {
    /**
     * Language Strings
     */
    private static final Message T_title =
            message("xmlui.ArtifactBrowser.SimpleSearch.title");

    private static final Message T_dspace_home =
            message("xmlui.general.dspace_home");

    private static final Message T_trail =
            message("xmlui.ArtifactBrowser.SimpleSearch.trail");

    private static final Message T_search_scope =
        message("xmlui.ArtifactBrowser.SimpleSearch.search_scope");

    private static final Message T_head =
            message("xmlui.ArtifactBrowser.SimpleSearch.head");

//    private static final Message T_search_label =
//            message("xmlui.discovery.SimpleSearch.search_label");

    private static final Message T_go = message("xmlui.general.go");
    private static final Message T_filter_label = message("xmlui.Discovery.SimpleSearch.filter_head");
    private static final Message T_filter_help = message("xmlui.Discovery.SimpleSearch.filter_help");
    private static final Message T_filter_current_filters = message("xmlui.Discovery.AbstractSearch.filters.controls.current-filters.head");
    private static final Message T_filter_new_filters = message("xmlui.Discovery.AbstractSearch.filters.controls.new-filters.head");
    private static final Message T_filter_controls_apply = message("xmlui.Discovery.AbstractSearch.filters.controls.apply-filters");
    private static final Message T_filter_controls_add = message("xmlui.Discovery.AbstractSearch.filters.controls.add-filter");
    private static final Message T_filter_controls_remove = message("xmlui.Discovery.AbstractSearch.filters.controls.remove-filter");
    private static final Message T_filters_show = message("xmlui.Discovery.AbstractSearch.filters.display");
    private static final Message T_filter_contain = message("xmlui.Discovery.SimpleSearch.filter.contains");
    private static final Message T_filter_equals = message("xmlui.Discovery.SimpleSearch.filter.equals");
    private static final Message T_filter_notcontain = message("xmlui.Discovery.SimpleSearch.filter.notcontains");
    private static final Message T_filter_notequals = message("xmlui.Discovery.SimpleSearch.filter.notequals");
    private static final Message T_filter_authority = message("xmlui.Discovery.SimpleSearch.filter.authority");
    private static final Message T_filter_notauthority = message("xmlui.Discovery.SimpleSearch.filter.notauthority");
    private static final Message T_did_you_mean = message("xmlui.Discovery.SimpleSearch.did_you_mean");
    private static final Message T_add_filter = message("xmlui.Discovery.SimpleSearch.filter_add");
    private static final Message T_filter_apply = message("xmlui.Discovery.SimpleSearch.filter_apply");
    private static final Message T_FILTERS_SELECTED = message("xmlui.ArtifactBrowser.SimpleSearch.filter.selected");
    private static final Message T_search_label =
            message("xmlui.discovery.SimpleSearch.search_label");

    private SearchService searchService = null;

    public SimpleSearch() {
        DSpace dspace = new DSpace();
        searchService = dspace.getServiceManager().getServiceByName(SearchService.class.getName(),SearchService.class);
    }


    /**
     * Add Page metadata.
     */
    public void addPageMeta(PageMeta pageMeta) throws WingException, SQLException {
        pageMeta.addMetadata("title").addContent(T_title);
        pageMeta.addTrailLink(contextPath + "/", T_dspace_home);

        DSpaceObject dso = HandleUtil.obtainHandle(objectModel);
        if ((dso instanceof org.dspace.content.Collection) || (dso instanceof Community)) {
            HandleUtil.buildHandleTrail(dso, pageMeta, contextPath, true);
        }

        pageMeta.addTrail().addContent(T_trail);
    }

    /**
     * build the DRI page representing the body of the search query. This
     * provides a widget to generate a new query and list of search results if
     * present.
     */
    public void addBody(Body body) throws SAXException, WingException,
            SQLException, IOException, AuthorizeException {

        Request request = ObjectModelHelper.getRequest(objectModel);
        String queryString = getQuery();

        // Build the DRI Body
        Division search = body.addDivision("search", "primary");
        //search.setHead(T_head);
        String searchUrl = ConfigurationManager.getProperty("dspace.url") + "/JSON/discovery/search";

        search.addHidden("discovery-json-search-url").setValue(searchUrl);
        DSpaceObject currentScope = getScope();
        if(currentScope != null){
            search.addHidden("discovery-json-scope").setValue(currentScope.getHandle());
        }
        search.addHidden("contextpath").setValue(contextPath);

        Map<String, String[]> fqs = getParameterFilterQueries();

        Division searchBoxDivision = search.addDivision("discovery-search-box", "discoverySearchBox");

        Division mainSearchDiv = searchBoxDivision.addInteractiveDivision("general-query",
                "discover", Division.METHOD_GET, "discover-search-box");

        List searchList = mainSearchDiv.addList("primary-search", List.TYPE_FORM);

        /*searchList.setHead(T_search_label);
        if (variableScope())
        {
            Select scope = searchList.addItem().addSelect("scope");
            scope.setLabel(T_search_scope);
            buildScopeList(scope);
        }*/

        Item searchBoxItem = searchList.addItem();
        Text text = searchBoxItem.addText("query", "home-search");
        text.setValue(queryString);
        searchBoxItem.addButton("submit", "home-search-button").setValue(T_search_label);
        if(queryResults != null && StringUtils.isNotBlank(queryResults.getSpellCheckQuery()))
        {
            Item didYouMeanItem = searchList.addItem("did-you-mean", "didYouMean");
            didYouMeanItem.addContent(T_did_you_mean);
            didYouMeanItem.addXref(getSuggestUrl(queryResults.getSpellCheckQuery()), queryResults.getSpellCheckQuery(), "didYouMean");
        }
		searchList.addItem().addXref("#", "Add filters", null, "show-filters");	

        DSpaceObject dso = HandleUtil.obtainHandle(objectModel);
        DiscoveryConfiguration discoveryConfiguration = SearchUtils.getDiscoveryConfiguration(dso);
        java.util.List<DiscoverySearchFilter> filterFields = discoveryConfiguration.getSearchFilters();
        java.util.List<String> filterTypes = DiscoveryUIUtils.getRepeatableParameters(request, "filtertype");
        java.util.List<String> filterOperators = DiscoveryUIUtils.getRepeatableParameters(request, "filter_relational_operator");
        java.util.List<String> filterValues = DiscoveryUIUtils.getRepeatableParameters(request,  "filter");
if(0 < filterTypes.size() || 0 < filterFields.size()){
            Division searchFiltersDiv = search.addInteractiveDivision("search-filters",
                    "discover", Division.METHOD_GET, "discover-search-box search");

            List secondarySearchList = searchFiltersDiv.addList("secondary-search", List.TYPE_FORM);
            //secondarySearchList.setHead(T_filter_label);

            if(0 < filterFields.size()){
            	
                Item item = secondarySearchList.addItem("search-filter-list", "search-filter-list");
                Composite filterComp = item.addComposite("search-filter-controls");
                filterComp.setLabel(T_add_filter);
                filterComp.setHelp(T_filter_help);

    //            filterComp.setLabel("");

                Select select = filterComp.addSelect("filtertype", "widthauto");

                //For each field found (at least one) add options
                for (DiscoverySearchFilter searchFilter : filterFields) {
                    select.addOption(searchFilter.getIndexFieldName(), message("xmlui.ArtifactBrowser.SimpleSearch.filter." + searchFilter.getIndexFieldName()));
                }

                Select operator = filterComp.addSelect("filter_relational_operator", "widthauto");
                operator.addOption("contains", "contains");
                operator.addOption("equals", "equals");
                operator.addOption("notcontains", "not contains");
                operator.addOption("notequals", "not equals");
                operator.addOption("notavailable", "not available");
                
                //Add a box so we can search for our value
                filterComp.addText("filter", "widthauto");

                //And last add an add button
                filterComp.enableAddOperation();

            }


    //        queryList.addItem().addContent("Filters");
            //If we have any filters, show them
            if(filterTypes.size() > 0){
                //if(filters != null && filters.size() > 0){
                List item = secondarySearchList.addList("used-filters", List.TYPE_GLOSS, "used-filters-list");

                item.setHead(T_FILTERS_SELECTED);

                for (int i = 0; i <  filterTypes.size(); i++) {
                    String filterType = filterTypes.get(i);
                    String filterOperator = filterOperators.get(i);
                    String filterValue = filterValues.get(i);
                    
                    if(StringUtils.isBlank(filterOperator)){
                        filterOperator = "contains";
                    } 
                    else if ("notavailable".equals(filterOperator))
                    {
                        filterValue = "[* TO *]";
                    }

                    if(StringUtils.isNotBlank(filterValue)){
                            DiscoverFilterQuery fq = searchService.toFilterQuery(context, filterType,filterOperator,filterValue);
                            
                            CheckBox box = item.addItem(null, fq.getOperator()).addCheckBox("fq");
                            Option option = box.addOption(true, fq.getFilterQuery());
                            String field = fq.getField();
                            option.addContent(message("xmlui.ArtifactBrowser.SimpleSearch.filter." + field));

                            //We have a filter query get the display value
                            //Check for a range query
                            Pattern pattern = Pattern.compile("\\[(.*? TO .*?)\\]");
                            Matcher matcher = pattern.matcher(fq.getDisplayedValue());
                            boolean hasPattern = matcher.find();
                            if (hasPattern) {
                                String[] years = matcher.group(0).replace("[", "").replace("]", "").split(" TO ");
                                option.addContent(": " + years[0] + " - " + years[1]);
                                continue;
                            }
                            option.addContent(": " + fq.getDisplayedValue());
                    }

                }
                secondarySearchList.addItem().addButton("submit_update_filters", "update-filters").setValue(T_filter_apply);
            }
            addHiddenFormFields("filter", request, fqs, searchFiltersDiv);
        }

        Division searchControlsDiv = search.addDivision("search-controls", "discover-sort-box search");
        buildSearchControls(searchControlsDiv);

        


//        query.addPara(null, "button-list").addButton("submit").setValue(T_go);

        // Build the DRI Body
        //Division results = body.addDivision("results", "primary");
        //results.setHead(T_head);

        // Add the result division
        try {
            buildSearchResultsDivision(search);
        } catch (SearchServiceException e) {
            throw new UIException(e.getMessage(), e);
        }

    }

    protected void addFilterRow(java.util.List<DiscoverySearchFilter> filterFields, int index, Row row, String selectedFilterType, String relationalOperator, String value) throws WingException {
        Select select = row.addCell("", Cell.ROLE_DATA, "selection").addSelect("filtertype_" + index);

        //For each field found (at least one) add options
        for (DiscoverySearchFilter searchFilter : filterFields)
        {
            select.addOption(StringUtils.equals(searchFilter.getIndexFieldName(), selectedFilterType), searchFilter.getIndexFieldName(), message("xmlui.ArtifactBrowser.SimpleSearch.filter." + searchFilter.getIndexFieldName()));
        }
        Select typeSelect = row.addCell("", Cell.ROLE_DATA, "selection").addSelect("filter_relational_operator_" + index);
        typeSelect.addOption(StringUtils.equals(relationalOperator, "contains"), "contains", T_filter_contain);
        typeSelect.addOption(StringUtils.equals(relationalOperator, "equals"), "equals", T_filter_equals);
        typeSelect.addOption(StringUtils.equals(relationalOperator, "authority"), "authority", T_filter_authority);
        typeSelect.addOption(StringUtils.equals(relationalOperator, "notcontains"), "notcontains", T_filter_notcontain);
        typeSelect.addOption(StringUtils.equals(relationalOperator, "notequals"), "notequals", T_filter_notequals);
        typeSelect.addOption(StringUtils.equals(relationalOperator, "notauthority"), "notauthority", T_filter_notauthority);
         



        //Add a box so we can search for our value
        row.addCell("", Cell.ROLE_DATA, "discovery-filter-input-cell").addText("filter_" + index, "discovery-filter-input").setValue(value == null ? "" : value);

        //And last add an add button
        Cell buttonsCell = row.addCell("filter-controls_" + index, Cell.ROLE_DATA, "filter-controls");
        buttonsCell.addButton("add-filter_" + index, "filter-control filter-add").setValue(T_filter_controls_add);
        buttonsCell.addButton("remove-filter_" + index, "filter-control filter-remove").setValue(T_filter_controls_remove);

    }

    @Override
    protected String getBasicUrl() throws SQLException {
        Request request = ObjectModelHelper.getRequest(objectModel);
        DSpaceObject dso = HandleUtil.obtainHandle(objectModel);

        return request.getContextPath() + (dso == null ? "" : "/handle/" + dso.getHandle()) + "/discover";
    }

    protected Map<String, String[]> getParameterFilterQueries(){
        return DiscoveryUIUtils.getParameterFilterQueries(ObjectModelHelper.getRequest(objectModel));

    }
    /**
     * Returns all the filter queries for use by discovery
     *  This method returns more expanded filter queries then the getParameterFilterQueries
     * @return an array containing the filter queries
     */
    protected String[] getFilterQueries() {
        return DiscoveryUIUtils.getFilterQueries(ObjectModelHelper.getRequest(objectModel), context);
    }


    /**
     * Get the search query from the URL parameter, if none is found the empty
     * string is returned.
     */
    protected String getQuery() throws UIException {
        Request request = ObjectModelHelper.getRequest(objectModel);
        String query = decodeFromURL(request.getParameter("query"));
        if (query == null)
        {
            return "";
        }
        return query.trim();
    }

    /**
     * Generate a url to the simple search url.
     */
    protected String generateURL(Map<String, String> parameters)
            throws UIException {
        String query = getQuery();
        if (!"".equals(query) && parameters.get("query") == null)
        {
            parameters.put("query", encodeForURL(query));
        }

        if (parameters.get("page") == null)
        {
            parameters.put("page", String.valueOf(getParameterPage()));
        }

        if (parameters.get("rpp") == null)
        {
            parameters.put("rpp", String.valueOf(getParameterRpp()));
        }


        if (parameters.get("group_by") == null)
        {
            parameters.put("group_by", String.valueOf(this.getParameterGroup()));
        }

        if (parameters.get("sort_by") == null && getParameterSortBy() != null)
        {
            parameters.put("sort_by", String.valueOf(getParameterSortBy()));
        }

        if (parameters.get("order") == null && getParameterOrder() != null)
        {
            parameters.put("order", getParameterOrder());
        }

        if (parameters.get("etal") == null)
        {
            parameters.put("etal", String.valueOf(getParameterEtAl()));
        }
        if(parameters.get("scope") == null && getParameterScope() != null)
        {
            parameters.put("scope", getParameterScope());
        }

        return AbstractDSpaceTransformer.generateURL("discover", parameters);
    }

    /**
     * Since the layout is creating separate forms for each search part
     * this method will add hidden fields containing the values from other form parts
     *
     * @param type the type of our form
     * @param request the request
     * @param fqs the filter queries
     * @param division the division that requires the hidden fields
     * @throws WingException will never occur
     */
    private void addHiddenFormFields(String type, Request request, Map<String, String[]> fqs, Division division) throws WingException {
        if(type.equals("filter") || type.equals("sort")){
            if(request.getParameter("query") != null){
                division.addHidden("query").setValue(request.getParameter("query"));
            }
            if(request.getParameter("scope") != null){
                division.addHidden("scope").setValue(request.getParameter("scope"));
            }
        }

        //Add the filter queries, current search settings so these remain saved when performing a new search !
        if(type.equals("search") || type.equals("sort"))
        {
            for (String parameter : fqs.keySet())
            {
                String[] values = fqs.get(parameter);
                for (String value : values) {
                    division.addHidden(parameter).setValue(value);
                }
            }
        }

        if(type.equals("search") || type.equals("filter")){
            if(request.getParameter("rpp") != null){
                division.addHidden("rpp").setValue(request.getParameter("rpp"));
            }
            if(request.getParameter("sort_by") != null){
                division.addHidden("sort_by").setValue(request.getParameter("sort_by"));
            }
            if(request.getParameter("order") != null){
                division.addHidden("order").setValue(request.getParameter("order"));
            }
        }
    }

    protected String getSuggestUrl(String newQuery) throws UIException {
        Map parameters = new HashMap();
        parameters.put("query", newQuery);
        return addFilterQueriesToUrl(generateURL(parameters));
    }
}
