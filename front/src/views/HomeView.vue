<template>
  <div>
    <div>看板</div>
  </div>
</template>

<script setup lang="ts">
import axios from 'axios'

axios.get('http://localhost:8080/home').then((response) => {

}).catch((error) => {})



</script>

<style scoped></style>
