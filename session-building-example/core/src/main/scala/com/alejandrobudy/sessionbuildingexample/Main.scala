/*
 * Copyright 2022 alejandrobudy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alejandrobudy.sessionbuildingexample

import cats.implicits._
import cats.Parallel
import cats.effect.implicits._
import cats.effect.{Async, IO, IOApp, MonadCancelThrow}
import com.alejandrobudy.sessionbuildingexample.domain.{Session, Subscription}
import com.alejandrobudy.sessionbuildingexample.repositories.{
  ProfileRepository,
  SubscriptionsRepository
}
import com.alejandrobudy.sessionbuildingexample.storage.BackingStore

import java.time.Instant
import java.util.UUID

object domain {
  case class Profile(id: String, profileName: String)
  case class Subscriptions(l: List[Subscription])
  case class Subscription(id: String, channel: String)

  case class Session(
      sessionId: UUID,
      profile: Profile,
      subscriptions: Subscriptions,
      createdAt: Instant
  )
}

object repositories {

  import domain._

  object ProfileRepository {
    def fetchProfile[F[_]: MonadCancelThrow]: F[Profile] =
      MonadCancelThrow[F].pure(Profile("profile-001", "Alejandro"))

  }

  object SubscriptionsRepository {
    def fetch[F[_]: MonadCancelThrow]: F[Subscriptions] =
      MonadCancelThrow[F].pure(
        Subscriptions(
          List(
            Subscription("sub-001", "Ibai"),
            Subscription("sub-001", "Sezar blue")
          )
        )
      )
  }
}

object storage {
  object BackingStore {
    def save[F[_]: MonadCancelThrow](session: Session): F[Unit]   = MonadCancelThrow[F].unit
    def revoke[F[_]: MonadCancelThrow](session: Session): F[Unit] = MonadCancelThrow[F].unit
  }
}

object Service {
  def create[F[_]: Async: Parallel]: F[Session] =
    for {
      session <- gatherInfo
      _       <- BackingStore.save(session)
    } yield session

  def updateSubscriptions[F[_]: Async: Parallel](session: Session): F[Session] = {
    val revokePreviousSession =
      BackingStore.revoke[F](session)

    for {
      _ <- revokePreviousSession.start // Don't care the result, we would use retries + logging
      newSession <- gatherInfo
      _          <- BackingStore.save(newSession)
    } yield newSession
  }

  private def gatherInfo[F[_]: Async: Parallel] = (
    Async[F].delay(UUID.randomUUID()),
    ProfileRepository.fetchProfile[F],
    SubscriptionsRepository.fetch[F],
    Async[F].delay(Instant.now())
  ).parMapN(Session.apply)
}

object Main extends IOApp.Simple {
  def run: IO[Unit] = Service.create[IO] >> Service.updateSubscriptions[IO]
}
