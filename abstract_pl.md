java,util.concurrent: Przez Multiwersum Wątków

Współbieżność to ta gałąź naszej dyscypliny, którą straszy się niegrzecznych juniorów, 
seniorzy opowiadają zatrważające historie z kolejnych awarii a architekci unikają jak ognia, 
bo kto by się przejmował, dołoży się zasobów na klastrze k8s.

Wielu programistów Java słyszało o ReentrantLock czy ArrayBlockingQueue, ale pakiet java.util.concurrent oferuje znacznie więcej narzędzi, które – odpowiednio użyte – pozwalają pisać bezpieczny, skalowalny i wydajny kod współbieżny (tutaj miejsce na sarkastyczny i demoniczny śmiech z zaświatów).

Celem tej prezentacji jest poznanie "concurrency primitives", które skrywa pakiet java.util.concurrent, pokazując praktyczne wykorzystanie mniej znanych komponentów takich jak Phaser, Exchanger, Semaphore, CountDownLatch czy różne implementacje kolejek. "Concurrency primitives" to klocki Lego, z których podczas
prezentacji zbudujamy takie rozwiązania jak cache, object pool,batch executor, actor system czy map reduce. 

Będzię też trochę o Java Memory Model, w ujęciu praktyczny, bez gnębienia ludzkości teoretycznymi rozważaniami, oraz o narzędziach i metodyce testowania kodu współbieżnego.
