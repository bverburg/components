/* 
 * JBoss, Home of Professional Open Source 
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved. 
 * See the copyright.txt in the distribution for a 
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use, 
 * modify, copy, or redistribute it subject to the terms and conditions 
 * of the GNU Lesser General Public License, v. 2.1. 
 * This program is distributed in the hope that it will be useful, but WITHOUT A 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details. 
 * You should have received a copy of the GNU Lesser General Public License, 
 * v.2.1 along with this distribution; if not, write to the Free Software 
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, 
 * MA  02110-1301, USA.
 */

package org.switchyard.component.common.rest.support;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.PUT;
import javax.ws.rs.QueryParam;

/**
 * REST interface for WarehouseService.
 *
 * @author Magesh Kumar B <mageshbk@jboss.com> (C) 2012 Red Hat Inc.
 */
@Path("/warehouse")
public interface WarehouseResource {

    @GET
    @Path("/item/{itemId}")
    @Produces({"application/xml","application/json"})
    public Item getItem(@PathParam("itemId") Integer itemId);

    @PUT
    @Path("/")
    @Consumes({"application/xml","application/json"})
    public String addItem(Item item) throws Exception;

    @POST
    @Path("/")
    @Consumes({"application/xml","application/*+json"})
    public String updateItem(Item item) throws Exception;

    @DELETE
    @Path("")
    public String removeItem(@QueryParam("itemId") Integer itemId) throws Exception;

    @GET
    @Path("/count/")
    public Integer getItemCount();
}
