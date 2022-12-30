package org.example.balancer;

import org.example.Node;

import java.util.List;

public class RoundRobinBalancer implements  LoadBalancer {
    private  int index = 0;

    @Override
    public Node next (List<Node> nodes) {
        if(nodes.isEmpty())
            throw new RuntimeException("No nodes active");

        index++;
        if (index >= nodes.size())
            index = 0;
        return nodes.get(index);
    }
}
