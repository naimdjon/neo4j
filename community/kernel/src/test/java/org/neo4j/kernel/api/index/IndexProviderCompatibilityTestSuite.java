/**
 * Copyright (c) 2002-2013 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.api.index;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.neo4j.helpers.collection.IteratorUtil.asSet;

import java.util.Iterator;

import org.junit.Test;

public abstract class IndexProviderCompatibilityTestSuite
{
    protected abstract SchemaIndexProvider createIndexProvider();

    @Test
    public void shouldProvidePopulatorThatAcceptsDuplicateEntries() throws Exception
    {
        // given
        SchemaIndexProvider indexProvider = createIndexProvider();

        // when
        IndexPopulator populator = indexProvider.getPopulator( 17, new IndexConfiguration( false ) );
        populator.create();
        populator.add( 1, "value1" );
        populator.add( 2, "value1" );
        populator.close( true );

        // then
        IndexAccessor accessor = indexProvider.getOnlineAccessor( 17, new IndexConfiguration( false ) );
        IndexReader reader = accessor.newReader();
        Iterator<Long> nodes = reader.lookup( "value1" );
        assertEquals( asSet( 1l, 2l ), asSet( nodes ) );
        reader.close();
        accessor.close();
    }

    @Test
    public void shouldProvidePopulatorThatEnforcesUniqueConstraints() throws Exception
    {
        // given
        SchemaIndexProvider indexProvider = createIndexProvider();

        // when
        IndexPopulator populator = indexProvider.getPopulator( 17, new IndexConfiguration( true ) );
        populator.create();
        populator.add( 1, "value1" );
        try
        {
            populator.add( 2, "value1" );
            populator.close( true );

            fail( "expected exception" );
        }
        // then
        catch ( IndexEntryConflictException conflict )
        {
            assertEquals( 1, conflict.getExistingNodeId() );
            assertEquals( "value1", conflict.getPropertyValue() );
            assertEquals( 2, conflict.getAddedNodeId() );
        }
    }

    @Test
    public void shouldProvideAccessorThatEnforcesUniqueConstraintsAgainstDataAddedOnline() throws Exception
    {
        // given
        SchemaIndexProvider indexProvider = createIndexProvider();

        IndexPopulator populator = indexProvider.getPopulator( 17, new IndexConfiguration( true ) );
        populator.create();
        populator.close( true );

        // when
        IndexAccessor accessor = indexProvider.getOnlineAccessor( 17, new IndexConfiguration( true ) );
        accessor.updateAndCommit( asList( NodePropertyUpdate.add( 1, 11, "value1", new long[]{4} ) ) );
        try
        {
            accessor.updateAndCommit( asList( NodePropertyUpdate.add( 2, 11, "value1", new long[]{4} ) ) );

            fail( "expected exception" );
        }
        // then
        catch ( IndexEntryConflictException conflict )
        {
            assertEquals( 1, conflict.getExistingNodeId() );
            assertEquals( "value1", conflict.getPropertyValue() );
            assertEquals( 2, conflict.getAddedNodeId() );
        }
    }

    @Test
    public void shouldProvideAccessorThatEnforcesUniqueConstraintsAgainstDataAddedThroughPopulator() throws Exception
    {
        // given
        SchemaIndexProvider indexProvider = createIndexProvider();

        IndexPopulator populator = indexProvider.getPopulator( 17, new IndexConfiguration( true ) );
        populator.create();
        populator.add( 1, "value1" );
        populator.close( true );

        // when
        IndexAccessor accessor = indexProvider.getOnlineAccessor( 17, new IndexConfiguration( true ) );
        try
        {
            accessor.updateAndCommit( asList( NodePropertyUpdate.add( 2, 11, "value1", new long[]{4} ) ) );

            fail( "expected exception" );
        }
        // then
        catch ( IndexEntryConflictException conflict )
        {
            assertEquals( 1, conflict.getExistingNodeId() );
            assertEquals( "value1", conflict.getPropertyValue() );
            assertEquals( 2, conflict.getAddedNodeId() );
        }
    }

    @Test
    public void shouldProvideAccessorThatEnforcesUniqueConstraintsAgainstDataAddedInSameTx() throws Exception
    {
        // given
        SchemaIndexProvider indexProvider = createIndexProvider();

        IndexPopulator populator = indexProvider.getPopulator( 17, new IndexConfiguration( true ) );
        populator.create();
        populator.close( true );

        // when
        IndexAccessor accessor = indexProvider.getOnlineAccessor( 17, new IndexConfiguration( true ) );
        try
        {
            accessor.updateAndCommit( asList(
                    NodePropertyUpdate.add( 1, 11, "value1", new long[]{4} ),
                    NodePropertyUpdate.add( 2, 11, "value1", new long[]{4} ) ) );

            fail( "expected exception" );
        }
        // then
        catch ( IndexEntryConflictException conflict )
        {
            assertEquals( "value1", conflict.getPropertyValue() );
            if ( conflict.getExistingNodeId() == 1 )
            {
                assertEquals( 2, conflict.getAddedNodeId() );
            }
            else
            {
                assertEquals( 2, conflict.getExistingNodeId() );
                assertEquals( 1, conflict.getAddedNodeId() );
            }
        }
    }
}