<template>
  <div class="container" @keyup.1="console.log(1)">
    <div class="item" style="width: 10px">1</div>
    <div class="item" style="width: 20px">2</div>
    <div class="item" style="width: 30px">3</div>
    <div class="item" style="width: 40px">4</div>
    <div class="item" style="width: 50px">5</div>
    <div class="item" style="width: 60px">6</div>
    <div class="item" style="width: 70px">7</div>
    <div class="item" style="width: 80px">8</div>
    <div class="item" style="width: 90px">9</div>
    <div class="item" style="width: 100px">10</div>
    <div class="item" style="width: 110px">11</div>
    <div class="item" style="width: 120px">12</div>
  </div>
</template>

<script setup lang="ts"></script>

<style scoped>
.container {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
}

.item {
  border: 1px solid black;
  margin: 1em;
}
</style>
