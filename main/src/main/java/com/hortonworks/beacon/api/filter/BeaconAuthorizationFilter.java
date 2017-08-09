/**
 *   Copyright  (c) 2016-2017, Hortonworks Inc.  All rights reserved.
 *
 *   Except as expressly permitted in a written agreement between you or your
 *   company and Hortonworks, Inc. or an authorized affiliate or partner
 *   thereof, any use, reproduction, modification, redistribution, sharing,
 *   lending or other exploitation of all or any part of the contents of this
 *   software is strictly prohibited.
 */

package com.hortonworks.beacon.api.filter;

import com.google.common.base.Strings;
import com.google.gson.JsonObject;
import com.hortonworks.beacon.authorize.BeaconAccessRequest;
import com.hortonworks.beacon.authorize.BeaconAuthorizationException;
import com.hortonworks.beacon.authorize.BeaconAuthorizer;
import com.hortonworks.beacon.authorize.BeaconAuthorizerFactory;
import com.hortonworks.beacon.authorize.BeaconResourceTypes;
import com.hortonworks.beacon.config.PropertiesUtil;
import com.hortonworks.beacon.log.BeaconLog;
import com.hortonworks.beacon.rb.MessageCode;

import org.apache.commons.lang3.StringUtils;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.hadoop.security.Groups;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
/**
 * This enforces simple authorization on resources if user is authenticated.
 */

public class BeaconAuthorizationFilter implements Filter {

    private static final BeaconLog LOG = BeaconLog.getLog(BeaconAuthorizationFilter.class);
    private static boolean isDebugEnabled = LOG.isDebugEnabled();
    private BeaconAuthorizer authorizer = null;

    private static final String BASE_URL = "/" + PropertiesUtil.BASE_API;
    private static final PropertiesUtil AUTHCONFIG=PropertiesUtil.getInstance();
    private static final String BEACON_AUTHORIZATION_ENABLED="beacon.authorization.enabled";
    private String coreSiteFile;
    private String hdfsSiteFile;

    public BeaconAuthorizationFilter() {
        if (isDebugEnabled) {
            LOG.debug("==> BeaconAuthorizationFilter() -- Now initializing the Apache Beacon Authorizer!!!");
        }
        try {
            authorizer = BeaconAuthorizerFactory.getBeaconAuthorizer();
            if (authorizer != null) {
                authorizer.init();
            } else {
                LOG.warn(MessageCode.MAIN_000144.name());
            }
        } catch (BeaconAuthorizationException e) {
            LOG.error(MessageCode.MAIN_000122.name(), e);
        }

    }

    @Override
    public void init(FilterConfig arg0) throws ServletException {
        coreSiteFile=AUTHCONFIG.getResourceFileName("core-site.xml");
        hdfsSiteFile=AUTHCONFIG.getResourceFileName("hdfs-site.xml");
    }

    @Override
    public void destroy() {
        if (isDebugEnabled) {
            LOG.debug("==> BeaconAuthorizationFilter destroy");
        }
        if (authorizer != null) {
            authorizer.cleanUp();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        if (isDebugEnabled) {
            LOG.debug("==> AuthorizationFilter.doFilter");
        }
        boolean isAuthorization = AUTHCONFIG.getBooleanProperty(BEACON_AUTHORIZATION_ENABLED, false);
        if (!isAuthorization) {
            chain.doFilter(req, res);
            return;
        }
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        String pathInfo = request.getRequestURI();
        Configuration conf = new Configuration();
        if (!Strings.isNullOrEmpty(pathInfo) && pathInfo.startsWith(BASE_URL)) {
            if (isDebugEnabled) {
                LOG.debug("{0} is a valid REST API request!!!", pathInfo);
            }
            String userName = null;
            Set<String> groups = new HashSet<>();
            HttpSession session = request.getSession();
            if (session != null) {
                if (session.getAttribute("username") != null) {
                    userName=(String) session.getAttribute("username");
                    LOG.info(MessageCode.MAIN_000096.name(), userName);
                    LOG.info(MessageCode.MAIN_000097.name(), coreSiteFile);
                    LOG.info(MessageCode.MAIN_000098.name(), hdfsSiteFile);
                    if (!StringUtils.isEmpty(userName)) {
                        if (!StringUtils.isEmpty(coreSiteFile) && !StringUtils.isEmpty(hdfsSiteFile)) {
                            conf.addResource(new Path(coreSiteFile));
                            conf.addResource(new Path(hdfsSiteFile));
                            Groups groupObj=new Groups(conf);
                            List<String> userGroups=null;
                            try{
                                userGroups=groupObj.getGroups(userName);
                            } catch(Exception ex) {
                                LOG.error(MessageCode.MAIN_000099.name(), userName, ex.getMessage());
                            }
                            if (userGroups!=null) {
                                for (String groupNames : userGroups) {
                                    LOG.info(MessageCode.MAIN_000100.name(), groupNames);
                                    groups.add(groupNames);
                                }
                            }
                        }
                    }
                }
            }
            BeaconAccessRequest beaconRequest = new BeaconAccessRequest(request, userName, groups);
            if (isDebugEnabled) {
                LOG.debug(
                        "============================"
                        + "\nUserName :: {0}\nGroups :: {1}\nURL :: {2}\nAction :: {3}\nrequest.getServletPath() ::"
                        + " {4}\n============================\n",
                        beaconRequest.getUser(), beaconRequest.getUserGroups(), request.getRequestURL(),
                        beaconRequest.getAction(), pathInfo);
            }

            boolean accessAllowed = false;

            Set<BeaconResourceTypes> beaconResourceTypes = beaconRequest.getResourceTypes();
            if (beaconResourceTypes.size() == 1 && beaconResourceTypes.contains(BeaconResourceTypes.UNKNOWN)) {
                // Allowing access to unprotected resource types
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Allowing access to unprotected resource types {0}", beaconResourceTypes);
                }
                accessAllowed = true;
            } else {
                try {
                    if (authorizer != null) {
                        accessAllowed = authorizer.isAccessAllowed(beaconRequest);
                    }
                } catch (BeaconAuthorizationException e) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error(MessageCode.MAIN_000101.name(), e);
                    }
                }
                if (isDebugEnabled) {
                    LOG.debug("Authorizer result :: {0}", accessAllowed);
                }
            }

            if (accessAllowed) {
                if (isDebugEnabled) {
                    LOG.debug("Access is allowed so forwarding the request!!!");
                }
                chain.doFilter(req, res);
            } else {
                JsonObject json = new JsonObject();
                json.addProperty("AuthorizationError", "You are not authorized for " + beaconRequest.getAction().name()
                        + " on " + beaconResourceTypes + " : " + beaconRequest.getResource());

                response.setContentType("application/json");
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.sendError(HttpServletResponse.SC_FORBIDDEN, json.toString());
                if (isDebugEnabled) {
                    LOG.debug(
                            "You are not authorized for {0} on {1} : {2}\n"
                                    + "Returning 403 since the access is blocked update!!!!",
                            beaconRequest.getAction().name(), beaconResourceTypes, beaconRequest.getResource());
                }
            }

        } else {
            LOG.info(MessageCode.MAIN_000102.name());
            unauthorized(response, "Unauthorized");
        }
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.sendError(403, message);
    }

}