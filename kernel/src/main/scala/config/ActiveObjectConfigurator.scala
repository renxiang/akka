/**
 * Copyright (C) 2009 Scalable Solutions.
 */

package se.scalablesolutions.akka.kernel.config

import config.ScalaConfig.{RestartStrategy, Component}
import javax.servlet.ServletContext

object ActiveObjectConfigurator extends Logging {

  private var configuration: ActiveObjectConfigurator = _

  // FIXME: cheating with only having one single, scope per ServletContext
  def registerConfigurator(conf: ActiveObjectConfigurator) = {
    configuration = conf
  }

  def getConfiguratorFor(ctx: ServletContext): ActiveObjectConfigurator = {
    configuration
    //configurations.getOrElse(ctx, throw new IllegalArgumentException("No configuration for servlet context [" + ctx + "]"))
  }
}

trait ActiveObjectConfigurator {
  /**
   * Returns the active abject that has been put under supervision for the class specified.
   *
   * @param clazz the class for the active object
   * @return the active object for the class
   */
  def getActiveObject(clazz: Class[_]): AnyRef
  def getActiveObjectProxy(clazz: Class[_]): ActiveObjectProxy
  def getExternalDependency[T](clazz: Class[T]): T
  def configureActiveObjects(restartStrategy: RestartStrategy, components: List[Component]): ActiveObjectConfigurator
  def inject: ActiveObjectConfigurator
  def supervise: ActiveObjectConfigurator
  def reset
  def stop
}
