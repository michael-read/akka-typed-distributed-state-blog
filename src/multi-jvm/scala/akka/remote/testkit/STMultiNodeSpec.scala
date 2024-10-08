package akka.remote.testkit

import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike;
/**
  * Hooks up MultiNodeSpec with ScalaTest
  */
trait STMultiNodeSpec extends MultiNodeSpecCallbacks with AnyWordSpecLike with Matchers with BeforeAndAfterAll {
  self: MultiNodeSpec =>

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()

  // Might not be needed anymore if we find a nice way to tag all logging from a node
  override implicit def convertToWordSpecStringWrapper(s: String): WordSpecStringWrapper =
    new WordSpecStringWrapper(s"$s (on node '${self.myself.name}', $getClass)")
}
