package org.springframework.datastore.graph.neo4j.fieldaccess;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.persistence.support.EntityInstantiator;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Michael Hunger
 * @since 11.09.2010
 */
public abstract class AbstractRelationshipFieldAccessor<ENTITY,STATE,TARGET,TSTATE> implements FieldAccessor<ENTITY,TARGET> {
    protected final RelationshipType type;
    protected final Direction direction;
    protected final Class<? extends TARGET> relatedType;
    protected final EntityInstantiator<TARGET, TSTATE> graphEntityInstantiator;

    public AbstractRelationshipFieldAccessor(Class<? extends TARGET> clazz, EntityInstantiator<TARGET, TSTATE> graphEntityInstantiator, Direction direction, RelationshipType type) {
        this.relatedType = clazz;
        this.graphEntityInstantiator = graphEntityInstantiator;
        this.direction = direction;
        this.type = type;
    }


    protected STATE checkUnderlyingNode(ENTITY entity) {
        if (entity==null) throw new IllegalStateException("Entity is null");
        STATE node = getState(entity);
        if (node != null) return node;
        throw new IllegalStateException("Entity must have a backing Node");
    }

    protected void removeMissingRelationships(Node node, Set<Node> targetNodes) {
        for ( Relationship relationship : node.getRelationships(type, direction) ) {
            if (!targetNodes.remove(relationship.getOtherNode(node)))
                relationship.delete();
        }
    }

    protected void createAddedRelationships(STATE node, Set<TSTATE> targetNodes) {
        for (TSTATE targetNode : targetNodes) {
            createSingleRelationship(node,targetNode);
        }
    }

    protected void checkNoCircularReference(Node node, Set<STATE> targetNodes) {
        if (targetNodes.contains(node)) throw new InvalidDataAccessApiUsageException("Cannot create a circular reference to "+ targetNodes);
    }

    protected Set<STATE> checkTargetIsSetOfNodebacked(Object newVal) {
        if (!(newVal instanceof Set)) {
            throw new IllegalArgumentException("New value must be a Set, was: " + newVal.getClass());
        }
        Set<STATE> nodes=new HashSet<STATE>();
        for (Object value : (Set<Object>) newVal) {
            if (!relatedType.isInstance(value)) {
                throw new IllegalArgumentException("New value elements must be "+relatedType);
            }
            nodes.add(getState((ENTITY)value));
        }
        return nodes;
    }

    protected ManagedFieldAccessorSet<ENTITY,TARGET> createManagedSet(ENTITY entity, Set<TARGET> result) {
        return new ManagedFieldAccessorSet<ENTITY,TARGET>(entity, result, this);
    }

    protected Set<TARGET> createEntitySetFromRelationshipEndNodes(ENTITY entity) {
        final Iterable<TSTATE> nodes = getStatesFromEntity(entity);
        final Set<TARGET> result = new HashSet<TARGET>();
        for (final TSTATE otherNode : nodes) {
            result.add(graphEntityInstantiator.createEntityFromState(otherNode, relatedType));
		}
        return result;
    }


    protected void createSingleRelationship(STATE start, TSTATE end) {
        if (end==null) return;
        switch(direction) {
            case OUTGOING : {
                obtainSingleRelationship(start, end);
                break;
            }
            case INCOMING :
                obtainSingleRelationship((STATE)end, (TSTATE)start);
                break;
            default : throw new InvalidDataAccessApiUsageException("invalid direction " + direction);
        }
    }

    protected abstract Relationship obtainSingleRelationship(STATE start, TSTATE end);

    protected abstract Iterable<TSTATE> getStatesFromEntity(ENTITY entity);

    protected abstract STATE getState(ENTITY entity);
}
