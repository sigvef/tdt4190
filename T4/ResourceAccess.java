/**
 * Data structure describing the server and ID of a resource in the distributed
 * transaction system.
 */
class ResourceAccess
{
  /**
   * The server where the resource is located
   */
  final Server server;
  /**
   * The ID of the server where the resource is located
   */
  final int serverId;
  /**
   * The ID of the resource. This number is not globally unique, only unique for a given server.
   */
  final int resourceId;

  /**
   * Creates a new ResourceAccess object.
   *
   * @param server   The server where the resource is located.
   * @param serverId The ID of the server where the resource is located.
   * @param resourceId The ID of the resource.
   */
  ResourceAccess(Server server, int serverId, int resourceId)
  {
    this.server = server;
    this.serverId = serverId;
    this.resourceId = resourceId;
  }

  /**
   * Checks whether or not two ResourceAccess objects represent the same resource.
   *
   * @param o The ResourceAccess to compare this ResourceAccess with.
   * @return Whether or not the two objects represent the same resource.
   */
  public boolean equals(Object o)
  {
    if (!(o instanceof ResourceAccess))
      return false;
    ResourceAccess lr = (ResourceAccess)o;
    return lr.resourceId == resourceId && lr.serverId == serverId;
  }
}
