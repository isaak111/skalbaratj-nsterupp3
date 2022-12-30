package org.example.balancer;

import org.example.Node;

import java.util.List;

public interface LoadBalancer {
    Node next (List<Node> nodes) ;
}
